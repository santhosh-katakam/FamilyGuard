package com.familyguard.android;

import java.util.concurrent.TimeUnit;
import okhttp3.*;
import org.json.*;

final class Api {
  static final OkHttpClient CLIENT =
      new OkHttpClient.Builder()
          .connectTimeout(20, TimeUnit.SECONDS)
          .readTimeout(60, TimeUnit.SECONDS)
          .callTimeout(90, TimeUnit.SECONDS)
          .build();
  static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  record Reply(int code, JSONObject body) {}

  static Reply post(String path, String token, JSONObject body) throws Exception {
    return request("POST", path, token, body);
  }

  static Reply request(String method, String path, String token, JSONObject body) throws Exception {
    Request.Builder request =
        new Request.Builder()
            .url(BuildConfig.API_URL + path)
            .method(method, body == null ? null : RequestBody.create(body.toString(), JSON));
    if (token != null) request.header("Authorization", "Bearer " + token);
    try (Response r = CLIENT.newCall(request.build()).execute()) {
      String text = r.body() == null ? "{}" : r.body().string();
      JSONObject result;
      try {
        result = new JSONObject(text);
      } catch (JSONException e) {
        result = new JSONObject();
      }
      return new Reply(r.code(), result);
    }
  }
}
