package com.flixr.dao;

import com.flixr.beans.MovieWithPrediction;
import com.flixr.beans.Prediction;
import com.flixr.exceptions.DAOException;
import com.flixr.interfaces.IPredictionDAO;
import com.flixr.threads.PredictionMatrixThread;
import com.flixr.threads.ReadModelCsvThread;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.flixr.configuration.ApplicationConstants.*;

public class PredictionDAO implements IPredictionDAO {

    private int totalCountOfMoviesInMatrix;
    private Set<Integer> distinctMovieIds;
    private double[][] correlationMatrix;
    private HashMap<Integer, Integer> movieIdToMatrixIndex; // MovieId -> Index

    private String matrixCsvFilePathPrefix; // location of trained model CSV

    /**
     * Constructor without in memory storage
     * Used for much faster querying / prediction generation
     */
    public PredictionDAO() throws DAOException {
        setTotalCountOfMoviesInMatrix();
        setDistinctMovieIds();

        // Toggle Between CSV or DB stored Correlation Matrix
        if (USE_CSV_MATRIX) {
            String path = System.getProperty("user.dir");
            String ratingInputFilePrefix = CSV_MATRIX_FILE_PATH + CSV_MATRIX_FILE_PREFIX + "-";
            matrixCsvFilePathPrefix = path + ratingInputFilePrefix;
            generateMatrixModelFromCSV();
        }
        else {
            generateMatrixModelFromDB();
        }

    }

    /**
     * Gets the Correlation between Movies in the trained Recommendation Model
     * @param movieId_i     MovieId in Matrix position i
     * @param movieId_j     MovieId in Matrix position j
     * @return  Correlation (i.e. average preference difference between Movie i and Movie j)
     */
    public double getAveragePreferenceDifference(int movieId_i, int movieId_j) {
        try {
            // Convert MovieId to MatrixIndex
            int i = movieIdToMatrixIndex.get(movieId_i);
            int j = movieIdToMatrixIndex.get(movieId_j);

            // Return Average Difference
            return correlationMatrix[i][j];

        } catch (IndexOutOfBoundsException | NullPointerException e) {
            //System.out.println("Matrix Index was Invalid!");
            //System.out.println("Assuming correlation to be 0.");
//            e.printStackTrace();
            return 0;
        }
    }


    private void generateMatrixModelFromDB() throws DAOException {

        // Track Progress
        System.out.println("Loading Correlation Matrix... ");

        // Initialize Matrix & Index Map
        correlationMatrix = new double[totalCountOfMoviesInMatrix][totalCountOfMoviesInMatrix];
        movieIdToMatrixIndex = new HashMap<>();

        // Map MovieId to Matrix Index
        int matrixIndx = 0;
        for (int movieId: distinctMovieIds) {
            movieIdToMatrixIndex.put(movieId, matrixIndx);
            matrixIndx++;
        }

        // Determine # of entries per subset of movie list (partitioned by thread count)
        int dividedCount = 1;
        try {
            dividedCount = (int) Math.floor( (double) distinctMovieIds.size() / PRED_ENGINE_THREADS ) ;
        } catch (ArithmeticException e) {
            System.out.println("Warning: Number of Prediction Engine Threads must be at least 1!");
            System.out.println("Proceeding with a single-threaded configuration: PRED_ENGINE_THREADS = 1");
        }

        // Determine split indices for new movie list sublists
        int currentIndx = 0;
        int[] splitIndxs = new int[PRED_ENGINE_THREADS + 1];
        for (int i = 0; i < splitIndxs.length - 1; i++) {
            splitIndxs[i] = currentIndx;
            currentIndx += dividedCount;
        }
        splitIndxs[splitIndxs.length - 1] = distinctMovieIds.size(); // any remainders will just get tacked on to the last thread

        // Convert TreeSet to ArrayList (to facilitate splitting by index)
        List<Integer> listOfDistinctMovieIds = new ArrayList<>(distinctMovieIds);

        // Spawn Threads for faster predictions
        ExecutorService executor = Executors.newFixedThreadPool(PRED_ENGINE_THREADS);
        try {
            for (int i = 1; i <= PRED_ENGINE_THREADS; i++) {
                // Pass in a subset of the movies
                Set<Integer> subsetOfDistinctMovieIds = new TreeSet<>( listOfDistinctMovieIds.subList(splitIndxs[i-1], splitIndxs[i]) ) ;
                // Run a service to compute a subset of the matrix
                PredictionMatrixThread predictionMatrixThread = new PredictionMatrixThread(i, subsetOfDistinctMovieIds, correlationMatrix, movieIdToMatrixIndex);
                executor.execute(predictionMatrixThread);
            }

            // Wait for threads to complete
            executor.shutdown();
            while (!executor.isTerminated()) {}

        } catch (RuntimeException e) {
            System.out.println("Unable to generate Correlation Matrix! Problem encountered within Threads!");
            e.printStackTrace();
            throw new DAOException(e);
        }

        System.out.println("Correlation Matrix Loaded.");
    }


    private void setDistinctMovieIds() throws DAOException {
        distinctMovieIds = new TreeSet<>();
        try {
            Connection conn = DriverManager.getConnection(DB_CONNECTION_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("SELECT DISTINCT MovieIDi FROM recenginemodel ORDER BY MovieIdi");
            ResultSet resultSet = stmt.executeQuery();

            // Iterate Over MovieIds
            while (resultSet.next()) {
                int movieId = resultSet.getInt("MovieIDi");
                distinctMovieIds.add(movieId);
            }

            conn.close();

        } catch (SQLException e) {
            throw new DAOException(e);
        }

    }

    /**
     * Determines the total # of Movies in the Matrix
     * @throws DAOException
     */
    private void setTotalCountOfMoviesInMatrix() throws DAOException {
        try {
            Connection conn = DriverManager.getConnection(DB_CONNECTION_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) AS MovieCount FROM (SELECT DISTINCT MovieIDi FROM recenginemodel) Q1");
            ResultSet resultSet = stmt.executeQuery();

            resultSet.next();
            totalCountOfMoviesInMatrix = resultSet.getInt("MovieCount");

            conn.close();
        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }

    /**
     * Create Movies with Predicted Ratings
     * @param predictions   List of Predictions from the Prediction Engine
     * @return  List of MoviesWithPredictions, aggregates Movie information with the Predicted Ratings
     * @throws DAOException
     */
    public List<MovieWithPrediction> getPredictedMovies(List<Prediction> predictions) throws DAOException {
        try {
            Connection conn = DriverManager.getConnection(DB_CONNECTION_URL, DB_USERNAME, DB_PASSWORD);
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM movies WHERE movieId = ?");

            // Iterate Over Predictions & Use MovieID to query for Movie data
            List<MovieWithPrediction> moviesWithPredictions = new ArrayList<>();
            for (Prediction prediction : predictions) {
                // Get Movie Data
                stmt.setInt(1, prediction.getMovieId());
                ResultSet resultSet = stmt.executeQuery();
                resultSet.next();

                // Create new Movie with Prediction
                MovieWithPrediction movieWithPrediction = new MovieWithPrediction();
                movieWithPrediction.setMovieID(resultSet.getInt("movieID"));
                movieWithPrediction.setMoviename(resultSet.getString("movieName"));
                movieWithPrediction.setReleasedate(resultSet.getString("releaseDate"));
                movieWithPrediction.setAgerating(resultSet.getString("ageRating"));
                movieWithPrediction.setActors(resultSet.getString("actors"));
                movieWithPrediction.setRuntime(resultSet.getInt("runtime"));
                movieWithPrediction.setDirector(resultSet.getString("director"));
                movieWithPrediction.setWriter(resultSet.getString("writer"));
                movieWithPrediction.setMoviePosterURL(resultSet.getString("posterURL"));

                // Set the Predicted Rating
                movieWithPrediction.setPredictedRating(prediction.getPredictedRating());

                // Append to List
                moviesWithPredictions.add(movieWithPrediction);
            }

            // Close and return
            conn.close();
            return moviesWithPredictions;

        } catch (SQLException e) {
            throw new DAOException(e);
        }
    }


    public List<MovieWithPrediction> getPredictedMoviesForUnderAgedUser(List<Prediction> predictions, int numberOfMovies) throws DAOException {

        // Filter out Movies by Rating
        List<MovieWithPrediction> filteredListOfMoviesWithPredictions = new ArrayList<>();
        List<MovieWithPrediction> allMoviesWithPredictions = this.getPredictedMovies(predictions);
        for(MovieWithPrediction movieWithPrediction : allMoviesWithPredictions) {
            // Check if ratings is valid (i.e. not in restricted list)
            String ageRating = movieWithPrediction.getAgerating();
            if (!RATINGS_NOT_FOR_UNDER_18_YEARS_OLD.contains(ageRating)) {
                filteredListOfMoviesWithPredictions.add(movieWithPrediction);
            }
            // Break Loop is # of desired recs are reach
            if (filteredListOfMoviesWithPredictions.size() >= numberOfMovies) {
                break;
            }
        }
        return filteredListOfMoviesWithPredictions;
    }


    private void generateMatrixModelFromCSV() throws DAOException {
        // Track Progress
        System.out.println("Loading Correlation Matrix... ");

        // Initialize Matrix & Index Map
        correlationMatrix = new double[totalCountOfMoviesInMatrix][totalCountOfMoviesInMatrix];
        movieIdToMatrixIndex = new HashMap<>();

        // Map MovieId to Matrix Index
        int matrixIndx = 0;
        for (int movieId: distinctMovieIds) {
            movieIdToMatrixIndex.put(movieId, matrixIndx);
            matrixIndx++;
        }

        // Spawn Threads for faster predictions (NOTE: # of model.csv files must align with threads!!!)
        ExecutorService executor = Executors.newFixedThreadPool(PRED_ENGINE_THREADS);
        try {
            for (int i = 1; i <= PRED_ENGINE_THREADS; i++) {
                // Read model CSV file x of y
                String currentFileName = matrixCsvFilePathPrefix + i + "-of-" + PRED_ENGINE_THREADS + ".csv";
                ReadModelCsvThread readModelCsvThread = new ReadModelCsvThread(i, currentFileName, movieIdToMatrixIndex, correlationMatrix);
                executor.execute(readModelCsvThread);
            }

            // Wait for threads to complete
            executor.shutdown();
            while (!executor.isTerminated()) {}


        } catch (RuntimeException e) {
            System.out.println("Unable to generate Correlation Matrix! Problem encountered within Threads!");
            e.printStackTrace();
            throw new DAOException(e);
        }

        System.out.println("Correlation Matrix Loaded.");

    }



}
