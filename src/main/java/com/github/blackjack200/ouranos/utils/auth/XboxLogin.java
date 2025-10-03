package com.github.blackjack200.ouranos.utils.auth;

import cn.hutool.core.util.URLUtil;
import cn.hutool.core.util.ZipUtil;
import com.google.gson.JsonObject;
import lombok.experimental.UtilityClass;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

//Translate to java from https://github.com/XboxReplay/xboxlive-auth
@UtilityClass
public class XboxLogin {
    private static final String XBOX_PRE_AUTH_URL = "https://login.live.com/oauth20_authorize.srf?client_id=00000000441cc96b&redirect_uri=https://login.live.com/oauth20_desktop.srf&response_type=token&display=touch&scope=service::user.auth.xboxlive.com::MBI_SSL&locale=en";

    private JsonObject getPreAuthToken() throws Exception {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(XBOX_PRE_AUTH_URL).openConnection();
        connection.setRequestMethod("GET");
        OtherUtil.setBaseHeaders(connection);
        String responce = new String(ZipUtil.unGzip(connection.getInputStream()));
        JsonObject resJson = new JsonObject();
        resJson.addProperty("urlPost", findArgs(responce, "urlPost\":\""));
        String argTmp = findArgs(responce, "sFTTag\":\"");
        argTmp = argTmp.substring(argTmp.indexOf("value=\\\"") + 8, argTmp.length()-3);
        resJson.addProperty("PPFT", argTmp);
        List<String> cookies = connection.getHeaderFields().get("Set-Cookie");
        StringBuilder allCookie = new StringBuilder();
        for (String cookie : cookies) {
            allCookie.append(cookie.split(";")[0]);
            allCookie.append("; ");
        }
        resJson.addProperty("cookie", allCookie.toString());
        return resJson;
    }

    public String getAccessToken(String username, String password) throws Exception {
        JsonObject preAuthToken = getPreAuthToken();
        HttpsURLConnection connection = (HttpsURLConnection) new URL(preAuthToken.get("urlPost").getAsString()).openConnection();
        connection.setRequestMethod("POST");
        OtherUtil.setBaseHeaders(connection);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Cookie", preAuthToken.get("cookie").getAsString());

        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(true);

        DataOutputStream dataOutputStream = new DataOutputStream(connection.getOutputStream());
        dataOutputStream.writeBytes(URLUtil.buildQuery(Map.of("login", username, "loginfmt", username, "passwd", password, "PPFT", preAuthToken.get("PPFT").getAsString()), StandardCharsets.UTF_8));
        dataOutputStream.flush();

        connection.connect();
        InputStream is = connection.getInputStream();
        var body = new String(ZipUtil.unGzip(is.readAllBytes()));

        String url = connection.getURL().toString(), hash, accessToken = "";
        hash = url.split("#")[1];
        String[] hashes = hash.split("&");
        for (String partHash : hashes) {
            if (partHash.split("=")[0].equals("access_token")) {
                accessToken = partHash.split("=")[1];
                break;
            }
        }
        is.close();
        return accessToken.replaceAll("%2b", "+");
    }

    private String findArgs(String str, String args) {
        if (str.contains(args)) {
            int pos = str.indexOf(args);
            String result = str.substring(pos + args.length());
            pos = result.indexOf("\",");
            result = result.substring(0, pos - 1);
            return result;
        } else {
            throw new IllegalArgumentException("CANNOT FIND ARGUMENT");
        }
    }
}