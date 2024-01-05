/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.onefin.ewallet.settlement.pgpas;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.springframework.stereotype.Service;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

@Service
public class PKICrypt {

	/**
	 * String to hold name of the encryption algorithm.
	 */
	public static String ALGORITHM = "RSA";
	public static int KEY_PAIR_LENGTH = 2048;
	public static String PRIVATE_KEY_FILE = "C:/keys/private.key";
	public static String PUBLIC_KEY_FILE = "C:/keys/public.key";

	/**
	 * Generate key which contains a pair of private and public key using 1024
	 * bytes. Store the set of keys in Prvate.key and Public.key files.
	 *
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public void generateKey() {
		try {
			KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
			keyGen.initialize(KEY_PAIR_LENGTH);
			KeyPair key = keyGen.generateKeyPair();
			PublicKey publicKey = key.getPublic();
			PrivateKey privateKey = key.getPrivate();

			File privateKeyFile = new File(PRIVATE_KEY_FILE);
			File publicKeyFile = new File(PUBLIC_KEY_FILE);

			// Create files to store public and private key
			if (privateKeyFile.getParentFile() != null) {
				privateKeyFile.getParentFile().mkdirs();
			}
			privateKeyFile.createNewFile();

			if (publicKeyFile.getParentFile() != null) {
				publicKeyFile.getParentFile().mkdirs();
			}
			publicKeyFile.createNewFile();

			// Saving the Public key in a file
			ObjectOutputStream publicKeyOS = new ObjectOutputStream(new FileOutputStream(publicKeyFile));
			publicKeyOS.writeObject(key.getPublic());
			publicKeyOS.close();

			// Saving the Private key in a file
			ObjectOutputStream privateKeyOS = new ObjectOutputStream(new FileOutputStream(privateKeyFile));
			privateKeyOS.writeObject(key.getPrivate());
			privateKeyOS.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * The method checks if the pair of public and private key has been generated.
	 *
	 * @return flag indicating if the pair of keys were generated.
	 */
	public boolean areKeysPresent() {

		File privateKey = new File(PRIVATE_KEY_FILE);
		File publicKey = new File(PUBLIC_KEY_FILE);

		if (privateKey.exists() && publicKey.exists()) {
			return true;
		}
		return false;
	}

	/**
	 * Encrypt the plain text using public key.
	 *
	 * @param text : original plain text
	 * @param key  :The public key
	 * @return Encrypted text
	 * @throws java.lang.Exception
	 */
	public byte[] encrypt(byte[] data, PublicKey publicKey) {
		byte[] cipherText = null;
		try {
			// get an RSA cipher object and print the provider
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			// encrypt the plain text using the public key
			cipher.init(Cipher.ENCRYPT_MODE, publicKey);
			cipherText = cipher.doFinal(data);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return cipherText;
	}

	/**
	 * Decrypt text using private key.
	 *
	 * @param text :encrypted text
	 * @param key  :The private key
	 * @return plain text
	 * @throws java.lang.Exception
	 */
	public byte[] decrypt(byte[] encData, PrivateKey privateKey) {
		byte[] dectyptedTextB = null;
		try {
			// get an RSA cipher object and print the provider
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			// decrypt the text using the private key
			cipher.init(Cipher.DECRYPT_MODE, privateKey);
			dectyptedTextB = cipher.doFinal(encData);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return dectyptedTextB;
	}

	public PublicKey getPublickey(String sPublicKeyName) {
		PublicKey pk = null;
		try {
			String sExtFile = getExtension(sPublicKeyName);
			if (sExtFile.equals("pem")) {
				File f = new File(sPublicKeyName);
				FileInputStream fis = new FileInputStream(f);
				DataInputStream dis = new DataInputStream(fis);
				byte[] keyBytes = new byte[(int) f.length()];
				dis.readFully(keyBytes);
				dis.close();
				String temp = new String(keyBytes);
				String publicKeyPEM = temp.replace("-----BEGIN PUBLIC KEY-----\n", "");
				publicKeyPEM = publicKeyPEM.replace("-----END PUBLIC KEY-----", "");
				Base64 b64 = new Base64();
				byte[] decoded = b64.decode(publicKeyPEM);

				X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
				KeyFactory kf = KeyFactory.getInstance("RSA");
				pk = kf.generatePublic(spec);
			} else if (sExtFile.equals("cer")) {
				FileInputStream fin = new FileInputStream(sPublicKeyName);
				CertificateFactory f = CertificateFactory.getInstance("X.509");
				X509Certificate certificate = (X509Certificate) f.generateCertificate(fin);
				pk = certificate.getPublicKey();
			} else if (sExtFile.equals("key")) {
				ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(PKICrypt.PUBLIC_KEY_FILE));
				pk = (PublicKey) inputStream.readObject();
			}
			return pk;
		} catch (Exception ex) {
			return null;
		}
	}

	public PrivateKey getPrivateKey(String sPrivateKeyName, String password) {
		PrivateKey pk = null;
		try {
			String sExtFile = getExtension(sPrivateKeyName);
			if (sExtFile.equals("pem")) {
				Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());

				FileReader fileReader = new FileReader(sPrivateKeyName);
				PEMReader pemReader = new PEMReader(fileReader, new DefaultPasswordFinder(password.toCharArray()));

				KeyPair keypair = (KeyPair) pemReader.readObject();
				pk = keypair.getPrivate();
			} else if (sExtFile.equals("key")) {
				ObjectInputStream inputStream = new ObjectInputStream(new FileInputStream(PKICrypt.PRIVATE_KEY_FILE));
				pk = (PrivateKey) inputStream.readObject();
			}
			return pk;
		} catch (Exception ex) {
			return null;
		}

	}

	private String getExtension(String sFile) {
		String extension = "";
		int i = sFile.lastIndexOf('.');
		int p = Math.max(sFile.lastIndexOf('/'), sFile.lastIndexOf('\\'));

		if (i > p) {
			extension = sFile.substring(i + 1);
		}
		return extension;

	}

	private class DefaultPasswordFinder implements PasswordFinder {

		private final char[] password;

		private DefaultPasswordFinder(char[] password) {
			this.password = password;
		}

		@Override
		public char[] getPassword() {
			return Arrays.copyOf(password, password.length);
		}
	}

}
