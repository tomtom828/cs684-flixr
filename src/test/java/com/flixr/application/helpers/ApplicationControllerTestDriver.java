package com.flixr.application.helpers;

import com.flixr.beans.MovieWithPrediction;
import com.flixr.beans.User;
import com.flixr.exceptions.ApiException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static com.flixr.configuration.ApplicationConstants.*;
import static com.flixr.configuration.ApplicationConstantsTest.API_URL;

/**
 * @author Green Team
 *
 * Test Driver for ApplicationControllerTests
 *
 */
public class ApplicationControllerTestDriver {

    /**
     * @author Thomas Thompson
     * Performs a POST request to the Java API
     *
     * @return  Returns User object if login was a success
     */
    public User signInUser(String userEmail, String userPassword) {

        // Headers for POST request
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> map= new LinkedMultiValueMap<>();
        map.add("email", userEmail);
        map.add("password", userPassword);

        // Create POST request
        String queryURL = API_URL + "/signin";
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<User> response = restTemplate.postForEntity(queryURL, request , User.class);

        // Collect Response form POST request
        User user = response.getBody();
        return user;
    }


    /**
     * @author Thomas Thompson
     * Performs a GET request to the Java API
     *
     * @return  Returns List of MoviesWithPredictions
     */
    public List<MovieWithPrediction> getMovieRecommendations(int userId, String sortType) {

        // Create GET request
        RestTemplate restTemplate = new RestTemplate();
        String queryURL = API_URL + "/recommend/" + userId + "/" + sortType;
        ResponseEntity<MovieWithPrediction[]> response = restTemplate.getForEntity(queryURL, MovieWithPrediction[].class);

        // Collect Response form POST request
        List<MovieWithPrediction> listOfMoviePredictions = Arrays.asList(response.getBody());
        return listOfMoviePredictions;
    }


    // TODO - Keep adding your helper methods for GET / POST requests here ...
    /**
     * Author: Zion Whitehall
     * System Test: Add user rating via API call
     */
    public void postMovieRating(int userID, int imdbID, double rating) //uses info from applicationcontroller
    {
        // Headers for POST request
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> map= new LinkedMultiValueMap<>(); //used for making the JSON
        map.add("user_id", userID + "");
        map.add("movie_id", imdbID + "");
        map.add("grade", rating + "");

        // Create POST request
        String queryURL = API_URL + "/rating"; //called from README
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<Object> response = restTemplate.postForEntity(queryURL, request , Object.class);

    }



}
