package com.flixr.application;


import com.flixr.beans.PredictedMovie;
import com.flixr.beans.UserSubmission;
import com.flixr.dao.EngineDAO;
import com.flixr.engine.PredictionEngine;
import com.flixr.beans.Prediction;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Thomas Thompson
 *
 * Calls on Recommendation Engine for training new models
 * Calls on Prediction Engine for getting movie recommendations
 * Calls on EngineDAO for getting results based on an existing model
 */
public class RecommendationController {

    private EngineDAO engineDAO;

    public RecommendationController() {
        engineDAO = new EngineDAO();
    }

    /**
     * Returns a list of top "X" movie predictions, sorted by highest to lowest predicted rating
     * @param userId                    UserId of user
     * @param numberOfMoviePredictions  "X" Number of predictions (ex. top "10")
     * @return Returns the top "X" movie predictions
     * @throws SQLException                 Thrown if DB Connection issue
     * @throws IndexOutOfBoundsException    Thrown if top "X" is greater than the number of predicted movies available
     */
    public List<PredictedMovie> getTopMoviePredictions(int userId, int numberOfMoviePredictions) throws SQLException, IndexOutOfBoundsException {

        // Get UserSubmission
        UserSubmission userSubmission = engineDAO.getUserSubmission(userId);

        // Get MovieIds not rated by user
        Collection<Integer> movieIdsNotRatedByUser = engineDAO.getMovieIdsNotRatedByUserId(userId);

        // Generate Prediction from Engine
        PredictionEngine predictionEngine = new PredictionEngine(userSubmission, movieIdsNotRatedByUser, engineDAO);
        predictionEngine.generatePredictions();

        // Get Top "X" movie predictions
        List<Prediction> predictions = predictionEngine.getTopXMoviePredictions(numberOfMoviePredictions);

        // TODO finish this part once Movie beans in are place
        // Iterate over predictions to create MoviePredictions
        List<PredictedMovie> predictedMovies = new ArrayList<>();
        for (Prediction prediction : predictions) {
            PredictedMovie predictedMovie = new PredictedMovie();
            predictedMovies.add(predictedMovie);
        }

        return predictedMovies;
    }

}
