package com.septianfujianto.woodroid.Woocommerce.Oauth;

/**
 * Created by Septian A. Fujianto on 11/14/2016.
 */

import android.text.TextUtils;

import com.septianfujianto.woodroid.Woocommerce.HttpMethod;
import com.woocommerse.OAuth1.services.TimestampServiceImpl;

import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;



import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * WooCommerce specific OAuth signature generator
 */
public class OAuthSignature {

    private static final String UTF_8 = "UTF-8";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SIGNATURE_METHOD_HMAC_SHA256 = "HMAC-SHA256";
    private static final String BASE_SIGNATURE_FORMAT = "%s&%s&%s";
    private static final String DELETE_PARAM_FORCE = "force";

    private OAuthSignature() {}

    public static Map<String, String> getAsMap(OAuthConfig config, String endpoint, HttpMethod httpMethod) {
        if (config == null || endpoint == null || httpMethod == null) {
            return Collections.emptyMap();
        }
        final String nonce = new TimestampServiceImpl().getNonce();
        final String timestamp = new TimestampServiceImpl().getTimestampInSeconds();

        Map<String,String> params = new HashMap<>();
        params.put(OAuthHeader.OAUTH_CONSUMER_KEY.getValue(), config.getConsumerKey());
        params.put(OAuthHeader.OAUTH_TIMESTAMP.getValue(), String.valueOf(System.currentTimeMillis() / 1000L));
        params.put(OAuthHeader.OAUTH_NONCE.getValue(), UUID.randomUUID().toString());
//        params.put(OAuthHeader.OAUTH_TIMESTAMP.getValue(), timestamp);
//        params.put(OAuthHeader.OAUTH_NONCE.getValue(), nonce);
        params.put(OAuthHeader.OAUTH_SIGNATURE_METHOD.getValue(), SIGNATURE_METHOD_HMAC_SHA256);

        // WooCommerce specified param
        if (HttpMethod.DELETE.equals(httpMethod)) {
            params.put(DELETE_PARAM_FORCE, Boolean.TRUE.toString());
        }
        String oAuthSignature = generateOAuthSignature(config.getConsumerSecret(), endpoint, httpMethod, params);
        params.put(OAuthHeader.OAUTH_SIGNATURE.getValue(), oAuthSignature);
        return params;
    }

    public static String getAsQueryString(OAuthConfig config, String endpoint, HttpMethod httpMethod) {
        if (config == null || endpoint == null || httpMethod == null) {
            return "";
        }

        Map<String, String> oauthParameters = getAsMap(config, endpoint, httpMethod);
        String encodedSignature = oauthParameters.get(OAuthHeader.OAUTH_SIGNATURE.getValue())
                .replace(SpecialSymbol.PLUS.getPlain(), SpecialSymbol.PLUS.getEncoded());
        oauthParameters.put(OAuthHeader.OAUTH_SIGNATURE.getValue(), encodedSignature);
        return mapToString(oauthParameters, SpecialSymbol.EQUAL.getPlain(), SpecialSymbol.AMP.getPlain());
    }


    private static String generateOAuthSignature(String customerSecret, String endpoint, HttpMethod httpMethod, Map<String, String> parameters) {
        String signatureBaseString = getSignatureBaseString(endpoint, httpMethod.name(), parameters);
        // v1, v2
        String secret = customerSecret + SpecialSymbol.AMP.getPlain();
        return signBaseString(secret, signatureBaseString);
    }

    private static String signBaseString(String secret, String signatureBaseString) {
        Mac macInstance;
        try {
            macInstance = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(UTF_8), HMAC_SHA256);
            macInstance.init(secretKey);
            String encodedString = new String(Base64.encodeBase64(macInstance.doFinal(signatureBaseString.getBytes(UTF_8))));
            return encodedString.replace('+','-').replace('/','_');
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, UTF_8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getSignatureBaseString(String url, String method, Map<String, String> parameters) {
        String requestURL = urlEncode(url);
        // 1. Percent encode every key and value that will be signed.
        Map<String, String> encodedParameters = percentEncodeParameters(parameters);

        // 2. Sort the list of parameters alphabetically by encoded key.
        encodedParameters = getSortedParameters(encodedParameters);
        String paramsString = mapToString(encodedParameters, SpecialSymbol.EQUAL.getEncoded(), SpecialSymbol.AMP.getEncoded());
        return String.format(BASE_SIGNATURE_FORMAT, method, requestURL, paramsString);
    }

    private static String map2String(Map<String, String> paramsMap, String keyValueDelimiter, String paramsDelimiter) {
//        return paramsMap.entrySet().stream()
//                .map(entry -> entry.getKey() + keyValueDelimiter + entry.getValue())
//                .collect(Collectors.joining(paramsDelimiter));

        return "";
    }

    public static String mapToString(Map<String, String> paramsMap, String keyValueDelimiter, String paramsDelimiter) {
        String[] fromMap = new String[paramsMap.size()];
        int i = 0;
        Map<String, String> map = paramsMap;

        for(Map.Entry<String, String> entry : map.entrySet()) {
            fromMap[i] = entry.getKey() + keyValueDelimiter + entry.getValue();
            i++;
        }


        return TextUtils.join(paramsDelimiter, fromMap);
    }


    private static Map<String, String> percentEncodeParameters(Map<String, String> parameters) {
        Map<String, String> encodedParamsMap = new HashMap<>();

        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            String key = parameter.getKey();
            String value = parameter.getValue();
            encodedParamsMap.put(percentEncode(key), percentEncode(value));
        }

        return encodedParamsMap;
    }

    private static String percentEncode(String s) {
        try {
            return URLEncoder.encode(s, UTF_8)
                    // OAuth encodes some characters differently:
                    .replace(SpecialSymbol.PLUS.getPlain(), SpecialSymbol.PLUS.getEncoded())
                    .replace(SpecialSymbol.STAR.getPlain(), SpecialSymbol.STAR.getEncoded())
                    .replace(SpecialSymbol.TILDE.getEncoded(), SpecialSymbol.TILDE.getPlain());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static Map<String, String> getSortedParameters(Map<String, String> parameters) {
        return new TreeMap<>(parameters);
    }
}
