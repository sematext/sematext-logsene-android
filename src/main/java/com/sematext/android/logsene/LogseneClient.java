package com.sematext.android.logsene;


import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

import com.sematext.android.Utils;

/**
 * Base client for interacting with Logsene API.
 *
 * Note: In most cases, you would want to use {@link com.sematext.android.Logsene} instead.
 */
public class LogseneClient {
  private final OkHttpClient client = new OkHttpClient();
  private final String receiverUrl;
  private final String appToken;
  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  /**
   * Constructor.
   *
   * @param receiverUrl the receiver (api) url
   * @param appToken the logsene app token
   */
  public LogseneClient(String receiverUrl, String appToken) {
    Utils.requireNonNull(receiverUrl);
    Utils.requireNonNull(appToken);
    receiverUrl = receiverUrl.trim();
    if (receiverUrl.endsWith("/")) {
      receiverUrl = receiverUrl.substring(0, receiverUrl.length() - 1);
    }
    this.receiverUrl = receiverUrl;
    this.appToken = appToken;
  }

  /**
   * Executes a bulk request.
   *
   * @see com.sematext.android.logsene.Bulk.Builder
   * @param bulk the bulk request
   * @return the response
   * @throws IOException if unable to send request
   */
  public ApiResponse execute(Bulk bulk) throws IOException {
    Utils.requireNonNull(bulk);
    Request request = new Request.Builder()
        .url(receiverUrl + "/_bulk")
        .post(bulk.toBody(appToken))
        .build();
    Response response = client.newCall(request).execute();
    return ApiResponse.fromHttpResponse(response);
  }
}
