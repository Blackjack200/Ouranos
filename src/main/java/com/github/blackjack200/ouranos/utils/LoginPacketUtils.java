package com.github.blackjack200.ouranos.utils;


import com.github.blackjack200.ouranos.Ouranos;
import com.github.blackjack200.ouranos.network.session.AuthData;
import com.github.blackjack200.ouranos.network.session.OuranosProxySession;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.cloudburstmc.protocol.bedrock.codec.v544.Bedrock_v544;
import org.cloudburstmc.protocol.bedrock.codec.v582.Bedrock_v582;
import org.cloudburstmc.protocol.bedrock.codec.v748.Bedrock_v748;
import org.cloudburstmc.protocol.bedrock.codec.v786.Bedrock_v786;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwx.HeaderParameterNames;
import org.jose4j.lang.JoseException;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.time.Instant;
import java.util.*;

@UtilityClass
public class LoginPacketUtils {
    @Getter
    private static final KeyPair privateKeyPair;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(Curve.P_384.toECParameterSpec());
            privateKeyPair = generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate private keyPair!", e);
        }
    }

    @SneakyThrows
    public static String createClientDataToken(KeyPair pair, String displayName, String xuid) {
        var publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        var now = Instant.now();

        var jwsObject = new JWTClaimsSet.Builder()
                .issuer("self")
                .notBeforeTime(Date.from(now.minusSeconds(3600)))
                .expirationTime(Date.from(now.plusSeconds(24 * 3600)))
                .issueTime(Date.from(now))
                .claim("cpk", publicKeyBase64)
                .claim("xid", xuid)
                .claim("xname", displayName)
                .build();

        var x5u = URI.create(publicKeyBase64);

        var signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES384).x509CertURL(x5u).build(),
                jwsObject);
        signedJWT.sign(new ECDSASigner((ECPrivateKey) pair.getPrivate()));
        return signedJWT.serialize();
    }

    @SneakyThrows
    public static String createChainDataJwt(KeyPair pair, Map<String, ?> extraData) {
        var publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        var now = Instant.now();
        var jwsObject = new JWTClaimsSet.Builder()
                .issuer("self")
                .notBeforeTime(Date.from(now.minusSeconds(3600)))
                .expirationTime(Date.from(now.plusSeconds(24 * 3600)))
                .issueTime(Date.from(now))
                .claim("certificateAuthority", true)
                .claim("extraData", extraData)
                .claim("randomNonce", UUID.randomUUID().getLeastSignificantBits())
                .claim("identityPublicKey", publicKeyBase64)
                .build();

        var x5u = URI.create(publicKeyBase64);

        var signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES384).x509CertURL(x5u).build(),
                jwsObject);
        signedJWT.sign(new ECDSASigner((ECPrivateKey) pair.getPrivate()));
        return signedJWT.serialize();
    }

    @SuppressWarnings("unchecked")
    @SneakyThrows
    public static String writeClientData(KeyPair pair, OuranosProxySession player, AuthData identityData, JSONObject clientData, boolean login_extra) {
        String publicKeyBase64 = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        JsonWebSignature jws = new JsonWebSignature();
        jws.setAlgorithmHeaderValue("ES384");
        jws.setHeader(HeaderParameterNames.X509_URL, publicKeyBase64);

        int outputProtocol = player.getUpstreamProtocolId();
        if (outputProtocol >= Bedrock_v544.CODEC.getProtocolVersion()) {
            clientData.putIfAbsent("TrustedSkin", true);
        }
        if (outputProtocol >= Bedrock_v582.CODEC.getProtocolVersion()) {
            clientData.putIfAbsent("CompatibleWithClientSideChunkGen", false);
            clientData.putIfAbsent("OverrideSkin", true);
        }
        if (outputProtocol >= Bedrock_v748.CODEC.getProtocolVersion()) {
            clientData.putIfAbsent("MaxViewDistance", 32);
            clientData.putIfAbsent("MemoryTier", 0);
            clientData.putIfAbsent("PlatformType", 0);
        }
        if (outputProtocol >= Bedrock_v786.CODEC.getProtocolVersion()) {
            clientData.putIfAbsent("GraphicsMode", 0);
        }
        clientData.putIfAbsent("IsEditorMode", false);
        clientData.putIfAbsent("SkinGeometryDataEngineVersion", "");
        clientData.putIfAbsent("ArmSize", "wide");
        clientData.putIfAbsent("CapeId", "");
        clientData.putIfAbsent("CapeImageHeight", 0);
        clientData.putIfAbsent("CapeImageWidth", 0);
        clientData.putIfAbsent("CapeOnClassicSkin", true);
        clientData.putIfAbsent("PersonaPieces", List.of());
        clientData.putIfAbsent("PieceTintColors", List.of());
        clientData.putIfAbsent("PersonaSkin", false);
        clientData.putIfAbsent("SkinAnimationData", "");
        clientData.putIfAbsent("SkinColor", "#b37b62");

        try (var stream = Ouranos.class.getClassLoader().getResourceAsStream("default_skin_geometry.json")) {
            clientData.putIfAbsent("SkinGeometryData", Base64.getEncoder().encodeToString(Objects.requireNonNull(stream).readAllBytes()));
        }
        try (var stream = Ouranos.class.getClassLoader().getResourceAsStream("default_skin_resource_patch.json")) {
            clientData.putIfAbsent("SkinResourcePatch", Base64.getEncoder().encodeToString(Objects.requireNonNull(stream).readAllBytes()));
        }

        clientData.putIfAbsent("SkinImageHeight", 64);
        clientData.putIfAbsent("SkinImageWidth", 64);
        clientData.putIfAbsent("ThirdPartyNameOnly", false);


        clientData.remove("SkinGeometryName");
        clientData.remove("SkinGeometry");

        //TODO AnimatedImageData rewrite
        clientData.put("AnimatedImageData", List.of());
        clientData.putIfAbsent("PlayFabId", "");


        if (login_extra) {
            clientData.put("OuranosXUID", identityData.xuid());
            clientData.put("OuranosIP", ((InetSocketAddress) player.downstream.getSocketAddress()).getHostString());
        }
        jws.setPayload(clientData.toJSONString());
        jws.setKey(pair.getPrivate());

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void validateClientData(JSONObject clientData) {
        var geometryData = Base64.getDecoder().decode(clientData.getOrDefault("SkinGeometryData", "").toString());
        if (geometryData.length > 1024 * 256) {
            throw new RuntimeException("Skin geometry data is too long");
        }
    }
}