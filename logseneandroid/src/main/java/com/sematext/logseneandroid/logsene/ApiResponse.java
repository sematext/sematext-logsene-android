package com.sematext.logseneandroid.logsene;

import android.util.Log;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Api response wrapper.
 */
public class ApiResponse {
  private final static String TAG = "ApiResponse";
  private Response httpResponse;
  private JSONObject json;
  private String body;

  private ApiResponse(Response httpResponse) {
    this.httpResponse = httpResponse;

    try {
      this.body = httpResponse.body().string();
      this.json = new JSONObject(body);
    } catch (JSONException e) {
      Log.e(TAG, "Unable to deserialize json response", e);
    } catch (IOException e) {
      Log.e(TAG, "IO exception while reading body", e);
    }
  }

  /**
   * Parses the response body as json and returns the wrapped response.
   * @param httpResponse original http response
   * @return wrapped response
   */
  public static ApiResponse fromHttpResponse(Response httpResponse) {
    return new ApiResponse(httpResponse);
  }

  public boolean isSuccessful() {
    return httpResponse.isSuccessful();
  }

  public Response getHttpResponse() {
    return httpResponse;
  }

  public JSONObject getJson() {
    return json;
  }

  public String getBody() {
    return body;
  }
}