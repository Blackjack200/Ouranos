package com.github.blackjack200.ouranos.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.util.Preconditions;
import org.jose4j.json.internal.json_simple.JSONObject;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.text.ParseException;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Various utilities for parsing Handshake data
 */
public class HandshakeUtils {

    private static final ECPublicKey MOJANG_PUBLIC_KEY_OLD;
    private static final ECPublicKey MOJANG_PUBLIC_KEY;

    private static final KeyPair privateKeyPair;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(Curve.P_384.toECParameterSpec());
            privateKeyPair = generator.generateKeyPair();

            MOJANG_PUBLIC_KEY_OLD = generateKey("MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE8ELkixyLcwlZryUQcu1TvPOmI2B7vX83ndnWRUaXm74wFfa5f/lwQNTfrLVHa2PmenpGI6JhIMUJaWZrjmMj90NoKNFSNBuKdm8rYiXsfaz3K36x/1U26HpG0ZxK/V1V");
            MOJANG_PUBLIC_KEY = generateKey("MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAECRXueJeTDqNRRgJi/vlRufByu/2G0i2Ebt6YMar5QX/R0DIIyrJMcUpruK4QveTfJSTp3Shlq4Gk34cD/4GUWwkv0DVuzeuB+tXija7HBxii03NHDbPAD0AKnLr2wdAp");
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate private keyPair!", e);
        }
    }


    public static SignedJWT createExtraData(KeyPair pair, JSONObject extraData) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        long timestamp = System.currentTimeMillis() / 1000;

        JSONObject dataChain = new JSONObject();
        dataChain.put("nbf", timestamp - 3600);
        dataChain.put("exp", timestamp + 24 * 3600);
        dataChain.put("iat", timestamp);
        dataChain.put("iss", "self");
        dataChain.put("certificateAuthority", true);
        dataChain.put("extraData", extraData);
        dataChain.put("randomNonce", UUID.randomUUID().getLeastSignificantBits());
        dataChain.put("identityPublicKey", publicKeyBase64);
        return encodeJWT(pair, dataChain);
    }

    public static SignedJWT encodeJWT(KeyPair pair, JSONObject payload) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        URI x5u = URI.create(publicKeyBase64);
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.ES384).x509CertURL(x5u).build();
        try {
            SignedJWT jwt = new SignedJWT(header, JWTClaimsSet.parse(payload.toString()));
            signJwt(jwt, (ECPrivateKey) pair.getPrivate());
            return jwt;
        } catch (JOSEException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    public static ECPublicKey generateKey(String b64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
    }

    public static void signJwt(JWSObject jws, ECPrivateKey key) throws JOSEException {
        jws.sign(new ECDSASigner(key, Curve.P_384));
    }
}
