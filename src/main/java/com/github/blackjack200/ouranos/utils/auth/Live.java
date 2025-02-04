package com.github.blackjack200.ouranos.utils.auth;

import cn.hutool.http.HttpUtil;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import java.io.OutputStream;
import java.time.Instant;
import java.util.Map;

@Log4j2
public class Live {
    @Data
    public static class DeviceAuthConnect {
        @SerializedName("user_code")
        private String userCode;
        @SerializedName("device_code")
        private String deviceCode;
        @SerializedName("verification_uri")
        private String verificationUri;
        private int interval;
        @SerializedName("expires_in")
        private int expiresIn;
    }

    @Data
    public static class DeviceAuthPoll {
        private String error;
        @SerializedName("error_description")
        private String errorDescription;
        @SerializedName("user_id")
        private String userId;
        @SerializedName("token_type")
        private String tokenType;
        private String scope;
        @SerializedName("access_token")
        private String accessToken;
        @SerializedName("refresh_token")
        private String refreshToken;
        @SerializedName("expires_in")
        private int expiresIn;
    }


    @AllArgsConstructor
    @Data
    public static class Token {

        // AccessToken is the token that authorizes and authenticates the requests.
        private String accessToken;

        // TokenType is the type of token, default is "Bearer".
        private String tokenType;

        // RefreshToken is the token used to refresh the access token if it expires.
        private String refreshToken;

        // Expiry is the optional expiration time of the access token.
        private Instant expiry;

        // ExpiresIn specifies how many seconds later the token expires.
        private long expiresIn;
    }

    private static DeviceAuthConnect startDeviceAuth() {
        var result = HttpUtil.post("https://login.live.com/oauth20_connect.srf", Map.of(
                "client_id", "0000000048183522",
                "scope", "service::user.auth.xboxlive.com::MBI_SSL",
                "response_type", "device_code"
        ));
        return new Gson().fromJson(result, DeviceAuthConnect.class);
    }

    private static Token pollDeviceAuth(String deviceCode) {
        var result = HttpUtil.post("https://login.live.com/oauth20_token.srf", Map.of(
                "client_id", "0000000048183522",
                "grant_type", "urn:ietf:params:oauth:grant-type:device_code",
                "device_code", deviceCode
        ));
        var poll = new Gson().fromJson(result, DeviceAuthPoll.class);
        if (poll.getError() != null && poll.getError().equals("authorization_pending")) {
            return null;
        } else if (poll.getError() == null) {
            return new Token(
                    poll.getAccessToken(),
                    poll.getTokenType(),
                    poll.getRefreshToken(),
                    Instant.now().plusSeconds(poll.getExpiresIn()),
                    poll.getExpiresIn()
            );
        }
        return null;
    }

    @SneakyThrows
    public static Token requestLiveTokenWriter(OutputStream out) {
        var d = startDeviceAuth();
        out.write(String.format("Authenticate at %s using the code %s\n", d.getVerificationUri(), d.getUserCode()).getBytes());
        Instant deadline = Instant.now().plusSeconds(d.getExpiresIn());
        Token token = null;
        while (Instant.now().isBefore(deadline) && token == null) {
            token = pollDeviceAuth(d.getDeviceCode());
            if (token != null) {
                return token;
            }
            Thread.sleep(1000);
        }
        return null;
    }
}

