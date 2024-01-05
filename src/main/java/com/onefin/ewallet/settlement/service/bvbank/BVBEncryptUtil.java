package com.onefin.ewallet.settlement.service.bvbank;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Service
public class BVBEncryptUtil {

	private static final Logger LOGGER = LoggerFactory.getLogger(BVBEncryptUtil.class);

	private static final String SHA256RSA = "SHA256withRSA";

	private static final String RSA = "RSA";


	public String signHex(String data, PrivateKey privateKey, String algorithm) {
		try {
			Signature signature = Signature.getInstance(algorithm);
			signature.initSign(privateKey);
			signature.update(data.getBytes(StandardCharsets.UTF_8));
			byte[] signedByteData = signature.sign();
//			return hex(Base64.encodeBase64(signedByteData));
			return hex(signedByteData);

		} catch (Exception e) {
			LOGGER.error("Cannot sign {}", algorithm, e);
		}
		return null;
	}

	public String signHex(String data, PrivateKey privateKey) {
		return signHex(data, privateKey, SHA256RSA);
	}


	public PrivateKey readPrivateKeyBVB(String filename) throws Exception {
		InputStream targetStream = new FileInputStream(filename);
		String privateKeyPEM = getKey(targetStream);
		return getPrivateKeyFromString(privateKeyPEM);

	}

	public static String getKey(InputStream is) throws IOException {
		// Read key from file
		String strKeyPEM = "";
		BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		String line;
		while ((line = br.readLine()) != null) {
			strKeyPEM += line + "\n";
		}
		br.close();
		return strKeyPEM;
	}

	public static RSAPrivateKey getPrivateKeyFromString(String key) throws IOException,
			GeneralSecurityException {
		String privateKeyPEM = key;
		privateKeyPEM =
				privateKeyPEM.replace("-----BEGIN PRIVATE KEY-----\n", "");
		privateKeyPEM = privateKeyPEM.replace("-----END PRIVATE KEY-----", "");
		byte[] encoded = org.apache.tomcat.util.codec.binary.Base64.decodeBase64(privateKeyPEM);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
		RSAPrivateKey privKey = (RSAPrivateKey) kf.generatePrivate(keySpec);
		return privKey;
	}

	public String MD5Hashing(String input) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			md.update(input.getBytes());
			byte[] digest = md.digest();
//			String myHash = uppercase == true ? DatatypeConverter.printHexBinary(digest).toUpperCase() : DatatypeConverter.printHexBinary(digest).toLowerCase();
//			return DatatypeConverter.printHexBinary(digest);
//			private String hex(byte[] data) {
			return hex(digest);
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	private String hex(byte[] data) {
		StringBuilder sb = new StringBuilder();
		for (byte b : data) sb.append(String.format("%02x", b & 0xFF));
		return sb.toString();
		//return printHexBinary(data).toLowerCase();
	}

	public boolean bvbVerifyHex(InputStream is, String message,
								String signature) throws Exception {

		PublicKey publicKey = getPublicKey(is);
		Signature sign = Signature.getInstance("SHA256withRSA");
		sign.initVerify(publicKey);
		sign.update(message.getBytes("UTF-8"));

		byte[] bytes = Hex.decodeHex(signature);
		return sign.verify(bytes);
//		decodeBase64
	}


	public static RSAPublicKey getPublicKey(InputStream is) throws IOException,
			GeneralSecurityException {
		String publicKeyPEM = getKey(is);
		return getPublicKeyFromString(publicKeyPEM);
	}

	public static RSAPublicKey getPublicKeyFromString(String key) throws IOException,
			GeneralSecurityException {
		String publicKeyPEM = key;
		publicKeyPEM =
				publicKeyPEM.replace("-----BEGIN PUBLIC KEY-----\n", "");
		publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");
		byte[] encoded = org.apache.tomcat.util.codec.binary.Base64.decodeBase64(publicKeyPEM);
		KeyFactory kf = KeyFactory.getInstance("RSA");
		X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
		return (RSAPublicKey) kf.generatePublic(spec);
	}


}
