/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.onefin.ewallet.settlement.pgpas;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

@Service
public class PGPas {

	@Autowired
	private PKICrypt pKICrypt;

	// java.util.Base64.Encoder base64en = java.util.Base64.getEncoder();
	private static java.util.Base64.Decoder base64de = java.util.Base64.getDecoder();
	// CÃ¡c kÃ½ tá»± xuá»‘ng dÃ²ng \r \n
	private static char eol1 = (char) 13;
	private static char eol2 = (char) 10;
	public static String SYM_ALGORITHM = "AES";
	public static int SESSION_KEY_LENGTH = 128;

	/**
	 * @param args the command line arguments
	 */
//	public static void main(String[] args) throws Exception {
////        String oriFile = "testpgp.txt";
//		String oriFile = "E:\\OneFin\\ewallet_3rd_integrate\\Napas\\File DS\\File DS\\Backend-PGPas\\Backend-PGPas\\060821_ACQ_ONEFINCE_971037_1_TC_ECOM.dat";
//		String pgpType = "dencrypt";
//		String keyDir = "E:\\OneFin\\ewallet_3rd_integrate\\Napas\\File DS\\File DS\\Backend-PGPas\\Backend-PGPas\\Key OF\\";
//		String encFile = oriFile + ".pgp";
//		String outFile = oriFile.substring(0, oriFile.lastIndexOf(".")) + "_out"
//				+ oriFile.substring(oriFile.lastIndexOf("."));
//		// Load public key
//		PublicKey publicKey = PKICrypt.getPublickey(keyDir + "ONEFIN_PGP_Publickey.cer");
//		// Load private key
//		PrivateKey privateKey = PKICrypt.getPrivateKey(keyDir + "ONEFIN_PGP_Privatekeys.pem", "");
//		if (pgpType.equals("encrypt")) {
//			PGPas.PGPencrypt(oriFile, encFile, publicKey);
//		} else {
//			PGPas.PGPdecrypt(encFile, outFile, privateKey);
//		}
////        PGPas.PGPencrypt(oriFile, encFile, publicKey);
////        PGPas.PGPdecrypt(encFile, outFile, privateKey);
//	}

	public void PGPencrypt(String originalFile, String encryptedFile, PublicKey publicKey) throws Exception {
		System.out.println(new Date().toString() + ":  ----Begin encrypt----");
		// Sinh khÃ³a phiÃªn ngáº«u nhiÃªn sá»­ dá»¥ng thuáº­t toÃ¡n AES-128-EBC
		SecretKey sessionKey = this.generateSessionkey();

		Path path = Paths.get(originalFile);
		byte[] data = Files.readAllBytes(path);
		System.out.println(new Date().toString() + ": Read file successfully");
		// MÃ£ hÃ³a dá»¯ liá»‡u sá»­ dá»¥ng khÃ³a phiÃªn Ä‘á»‘i xá»©ng AES
		byte[] encData = symmetricEncrypt(data, sessionKey);
		System.out.println(new Date().toString() + ": Encrypt data successfully");
		// MÃ£ hÃ³a khÃ³a phiÃªn sá»­ dá»¥ng thuáº­t toÃ¡n mÃ£ hÃ³a báº¥t Ä‘á»‘i xá»©ng
		// RSA vá»›i public key.
		byte[] sessionKeyByte = sessionKey.getEncoded();
		byte[] encSessionKey = pKICrypt.encrypt(sessionKeyByte, publicKey);
		System.out.println(new Date().toString() + ": Encrypt session key successfully");

		// Encode base64 khÃ³a phiÃªn vÃ  dá»¯ liá»‡u sau khi Ä‘Æ°á»£c mÃ£ hÃ³a
		String base64EncData = Base64.encode(encData);
		String base64EncSessionKey = Base64.encode(encSessionKey).replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "");
		System.out.println(new Date().toString() + ": Encode base64 successfully");
		// Ghi ra file, khÃ³a phiÃªn vÃ  dá»¯ liá»‡u sau khi Ä‘Æ°á»£c mÃ£ hÃ³a náº±m
		// trÃªn 2 dÃ²ng
		BufferedWriter bw = new BufferedWriter(new FileWriter(encryptedFile));
		bw.write(base64EncSessionKey);
		// chÃ¨n thÃªm kÃ½ tá»± xuá»‘ng dÃ²ng
		bw.write(eol1);
		bw.write(eol2);
		bw.write(base64EncData);
		bw.flush();
		bw.close();
		System.out.println(new Date().toString() + ": Write encrypted file successfully");
	}

	public void PGPdecrypt(String encryptedFile, String decryptedFile, PrivateKey privateKey) throws Exception {
		System.out.println(new Date().toString() + ":  ----Begin decrypt----");
		Path path = Paths.get(encryptedFile);
		byte[] allContent = Files.readAllBytes(path);
		System.out.println(new Date().toString() + ": Read file successfully");

		// Loáº¡i bá»� cÃ¡c kÃ½ tá»± xuá»‘ng dÃ²ng vÃ´ nghÄ©a á»Ÿ Ä‘áº§u file
		int i = 0, s = 0;
		while (((char) allContent[i] == eol1) || ((char) allContent[i] == eol2))
			i++;
		s = i;
		// TÃ¬m Ä‘áº¿n kÃ½ tá»± xuá»‘ng dÃ²ng Ä‘á»ƒ cáº¯t chuá»—i
		while ((eol1 != (char) allContent[i]) && (eol2 != (char) allContent[i]))
			i++;
		// Cáº¯t láº¥y pháº§n khÃ³a phiÃªn Ä‘Æ°á»£c mÃ£ hÃ³a vÃ  encode
		byte[] base64EncSessionKey = Arrays.copyOfRange(allContent, s, i);
		// Loáº¡i bá»� cÃ¡c kÃ½ tá»± xuá»‘ng dÃ²ng vÃ´ nghÄ©a á»Ÿ giá»¯a file
		while (((char) allContent[i] == eol1) || ((char) allContent[i] == eol2))
			i++;
		int len = allContent.length;
		// Loáº¡i bá»� cÃ¡c kÃ½ tá»± xuá»‘ng dÃ²ng vÃ´ nghÄ©a á»Ÿ cuá»‘i file
		while (((char) allContent[len - 1] == eol1) || ((char) allContent[len - 1] == eol2))
			len--;
		// Cáº¯t láº¥y pháº§n dá»¯ liá»‡u Ä‘Ã£ mÃ£ hÃ³a vÃ  encode
		byte[] base64EncData = Arrays.copyOfRange(allContent, i, len);

		// Decode base64 khÃ³a vÃ  dá»¯ liá»‡u
		byte[] encSessionKey = base64de.decode(base64EncSessionKey);
		byte[] decData = base64de.decode(base64EncData);
		System.out.println(new Date().toString() + ": Decode base64 successfully");
		// Giáº£i mÃ£ khÃ³a phiÃªn sá»­ dá»¥ng private key
		byte[] sessionKeyByte = pKICrypt.decrypt(encSessionKey, privateKey);
		SecretKey sessionKey = new SecretKeySpec(sessionKeyByte, PGPas.SYM_ALGORITHM);
		System.out.println(new Date().toString() + ": Decrypt session key successfully");
		// Giáº£i mÃ£ dá»¯ liá»‡u sá»­ dá»¥ng khÃ³a phiÃªn láº¥y Ä‘Æ°á»£c trong bÆ°á»›c
		// trÆ°á»›c
		byte[] data = symmetricDecrypt(decData, sessionKey);
		System.out.println(new Date().toString() + ": Decrypt date successfully");
		// Ghi file
		path = Paths.get(decryptedFile);
		Files.write(path, data);
		System.out.println(new Date().toString() + ": Write data file successfully");

	}

	private SecretKey generateSessionkey() {
		KeyGenerator keyGen;
		try {
			keyGen = KeyGenerator.getInstance(SYM_ALGORITHM);
			keyGen.init(SESSION_KEY_LENGTH);
			return keyGen.generateKey();
		} catch (NoSuchAlgorithmException ex) {
			Logger.getLogger(PGPas.class.getName()).log(Level.SEVERE, null, ex);
			return null;
		}
	}

	private byte[] symmetricEncrypt(byte[] messageB, SecretKey key) throws Exception {
		// SecretKey key = new SecretKeySpec(keyBytes, "DESede");
		Cipher cipher = Cipher.getInstance(SYM_ALGORITHM);
		cipher.init(Cipher.ENCRYPT_MODE, key);
		byte[] buf = cipher.doFinal(messageB);
		return buf;
	}

	private byte[] symmetricDecrypt(byte[] encryptedTextB, SecretKey key) throws Exception {

		Cipher decipher = Cipher.getInstance(SYM_ALGORITHM);
		decipher.init(Cipher.DECRYPT_MODE, key);

		byte[] plainText = decipher.doFinal(encryptedTextB);
		return plainText;
	}

}
