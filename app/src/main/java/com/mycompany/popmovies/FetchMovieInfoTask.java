package com.mycompany.popmovies;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.mycompany.popmovies.data.MoviesContract;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Vector;

/**
 *  Class for connecting to TMDB API, fetching JSON string, writing information into DB
 */

public class FetchMovieInfoTask extends AsyncTask<String, Void, Void> {

    private final String LOG_TAG = FetchMovieInfoTask.class.getSimpleName();
    private final Context mContext;

    FetchMovieInfoTask(Context context){
        mContext = context;
    }

    @Override
    protected Void doInBackground(String... params){
        if (params.length == 0){
            return null;
        }

        HttpURLConnection urlCOnnection = null;
        BufferedReader reader = null;

        String movieInfoJsonStr = null;
        String apiKey = "fa2461a57ac80bd28b2dc05dcb78f1e6"; //delete API key when sharing

        try {
            final String MOVIEINFO_BASE_URL = "http://api.themoviedb.org/3/discover/movie?";
            final String MOVIEINFO_SORT = "sort_by";
            final String MOVIEINFO_APIKEY = "api_key";
            Uri builtUri = Uri.parse(MOVIEINFO_BASE_URL).buildUpon().
                    appendQueryParameter(MOVIEINFO_SORT, params[0]).
                    appendQueryParameter(MOVIEINFO_APIKEY, apiKey).build();
            URL url = new URL(builtUri.toString());
            urlCOnnection = (HttpURLConnection) url.openConnection();
            urlCOnnection.setRequestMethod("GET");
            urlCOnnection.connect();

            InputStream inputStream = urlCOnnection.getInputStream();
            StringBuffer buffer = new StringBuffer();
            if (inputStream == null){
                return null;
            }

            reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;

            while ((line = reader.readLine()) != null) {
                buffer.append(line + "\n");
            }

            if (buffer.length() == 0){
                return null;
            }

            movieInfoJsonStr = buffer.toString();
            getMovieInfoDataFromJason(movieInfoJsonStr);

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error ", e);
        } catch (JSONException e){
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        } finally {
            if (urlCOnnection !=null){
                urlCOnnection.disconnect();
            }
            if (reader !=null){
                try {
                    reader.close();
                } catch (final IOException e){
                    Log.e(LOG_TAG, "Error closing stream", e);
                }
            }
        }


        return null;
    }



    private void getMovieInfoDataFromJason (String movieInfoJsonStr) throws JSONException {
        final String TMDB_RESULTS = "results";
        final String TMDB_IMG_URL = "poster_path";
        final String TMDB_ID = "id";
        final String TMDB_TITLE = "original_title";
        final String TMDB_OVERVIEW = "overview";
        final String TMDB_RELEASE_DATE = "release_date";
        final String TMDB_RAITING = "vote_average";
        final String TMDB_POPULARITY = "popularity";
        final String imageUrlBase = "http://image.tmdb.org/t/p/w342";

        try {
            JSONObject movieDataJason = new JSONObject(movieInfoJsonStr);
            JSONArray movieDataArray = movieDataJason.getJSONArray(TMDB_RESULTS);

            Vector<ContentValues> cVVector = new Vector<>(movieDataArray.length());

            /*Clear table of "un-favorites" before inserting new batch*/
            Utility.clearButFavorites(mContext);

            /*Check if DB is not empty. If not empty - there are Favs*/
            Cursor cursor = mContext.getContentResolver().query(MoviesContract.MoviesEntry.buildMoviesUri(),
                    new String[]{MoviesContract.MoviesEntry.COLUMN_MDB_ID, MoviesContract.MoviesEntry._ID}, null, null, null);


            for (int i =0; i<movieDataArray.length(); i++){

                JSONObject movieInfo = movieDataArray.getJSONObject(i);

                ContentValues moviesValues = new ContentValues();

                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_NAME, movieInfo.getString(TMDB_TITLE));
                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_POSTER_PATH, imageUrlBase + movieInfo.getString(TMDB_IMG_URL));
                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_RELEASE_DATE, movieInfo.getString(TMDB_RELEASE_DATE));
                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_OVERVIEW, movieInfo.getString(TMDB_OVERVIEW));
                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_MDB_ID, movieInfo.getString(TMDB_ID));
                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_VOTE_AVERAGE, movieInfo.getString(TMDB_RAITING));
                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_POPULARITY, movieInfo.getString(TMDB_POPULARITY));
                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_FAV_MOVIE, "false");
                moviesValues.put(MoviesContract.MoviesEntry.COLUMN_RUNTIME, 0);

                boolean isInDB = false;
                    while (cursor.moveToNext()) {
                        if (movieInfo.getString(TMDB_ID).equals(cursor.getString(0))){
                            isInDB = true;
                            break;

                        }
                    }
                if (!isInDB){
                    /*Movie is not in DB, lets add it*/
                    mContext.getContentResolver().insert(MoviesContract.MoviesEntry.CONTENT_URI, moviesValues);
                } else {
                    /*Movie is in DB, lets update it's rating and popularity*/
                    ContentValues values = new ContentValues();
                    values.put(MoviesContract.MoviesEntry.COLUMN_POPULARITY, movieInfo.getString(TMDB_POPULARITY));
                    values.put(MoviesContract.MoviesEntry.COLUMN_VOTE_AVERAGE, movieInfo.getString(TMDB_RAITING));

                    mContext.getContentResolver().update(
                            MoviesContract.MoviesEntry.buildMoviesUriWithID(Long.parseLong(cursor.getString(1))),
                            values,
                            null,
                            null
                    );


                }

                cursor.moveToPosition(-1);

                //cVVector.add(moviesValues);
            }


            // add to database
/*            if ( cVVector.size() > 0 ) {
                ContentValues[] cvArray = new ContentValues[cVVector.size()];
                cVVector.toArray(cvArray);
                if (!cursor.moveToFirst()) {
                    Log.v(LOG_TAG, "No cursor");
                    mContext.getContentResolver().bulkInsert(MoviesContract.MoviesEntry.CONTENT_URI, cvArray);
                }
            }*/

            cursor.close();
        } catch (JSONException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
            e.printStackTrace();
        }
    }





    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
    }





}
