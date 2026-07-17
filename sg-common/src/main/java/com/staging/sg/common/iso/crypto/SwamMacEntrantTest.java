package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import com.staging.sg.common.iso.SwamPackager;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.util.Arrays;

/**
 * Verification du MAC du SIGN-ON de CAM (membre) :
 *   - Message : 1804/801 signon de CAM
 *   - Cle     : ZMK_VISA -> TAK en clair (recette validee sur Nabil 9AA02ED9)
 *   - Algo    : ISO 9797 Alg 1 en 3DES-CBC, padding zeros
 *   - Buffer  : DEs bruts depuis l'ISOMsg (sans MTI/bitmap), recette M6
 *   - Cible   : 9AA02ED9
 */
public class SwamMacEntrantTest {

    // ZMK_VISA
    static final String ZMK_VISA    = "13AED5DA1F32347523C708C11F2608FD"; // KCV 2D617C
    // TAK sous ZMK_VISA
    static final String TAK_SOUS_ZMK = "E3C8EF7C4EEF54D81CE43CE2BCF33E37";
    // Cible
    static final String CIBLE       = "C6029E72";

    static byte[] k3(byte[] k){ byte[] r=new byte[24]; System.arraycopy(k,0,r,0,16); System.arraycopy(k,0,r,16,8); return r; }
    static String kcv(byte[] k) throws Exception {
        Cipher c=Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k3(k),"DESede"));
        return ISOUtil.hexString(Arrays.copyOfRange(c.doFinal(new byte[8]),0,3)).toUpperCase();
    }
    static byte[] zeroPad(byte[] d){ int r=d.length%8; if(r==0&&d.length>0)return d; return Arrays.copyOf(d,d.length+(8-r)); }

    /** Dechiffre la TAK sous ZMK (3DES-ECB). */
    static byte[] decryptTak(String takHex, String zmkHex) throws Exception {
        Cipher c=Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(k3(ISOUtil.hex2byte(zmkHex)),"DESede"));
        return c.doFinal(ISOUtil.hex2byte(takHex));
    }

    /** ISO 9797 Alg 1 en 3DES-CBC, padding zeros. */
    static byte[] macAlg1_3des(byte[] key16, byte[] data) throws Exception {
        byte[] p=zeroPad(data);
        Cipher c=Cipher.getInstance("DESede/CBC/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k3(key16),"DESede"),new IvParameterSpec(new byte[8]));
        byte[] e=c.doFinal(p); return Arrays.copyOfRange(e,e.length-8,e.length);
    }
    /** Recette du MAIL : ISO 9797 Alg 1 en DES SIMPLE (K1 = 8 premiers octets). */
    static byte[] macMail(byte[] key16, byte[] data) throws Exception {
        byte[] p=zeroPad(data);
        byte[] k1=Arrays.copyOfRange(key16,0,8);
        Cipher c=Cipher.getInstance("DES/CBC/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k1,"DES"),new IvParameterSpec(new byte[8]));
        byte[] e=c.doFinal(p); return Arrays.copyOfRange(e,e.length-8,e.length);
    }

    static boolean isLLVAR(int f){ switch(f){case 2:case 32:case 33:case 35:case 43:case 45:case 53:case 56:case 93:case 94:case 100:case 101:case 102:case 103:return true;default:return false;} }
    static boolean isLLLVAR(int f){ switch(f){case 46:case 48:case 54:case 55:case 60:case 61:case 62:case 123:case 127:return true;default:return false;} }

    /** DEs bruts depuis l'ISOMsg, sans MTI/bitmap/DE128. */
    static byte[] buildRaw(ISOMsg m, boolean withPrefixes) throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        for(int f=2; f<=127; f++){
            if(f==128||!m.hasField(f)) continue;
            String v=m.getString(f); if(v==null) continue;
            byte[] vb=v.getBytes("ISO-8859-1");
            if(withPrefixes){
                if(isLLLVAR(f)) out.write(String.format("%03d",vb.length).getBytes("US-ASCII"));
                else if(isLLVAR(f)) out.write(String.format("%02d",vb.length).getBytes("US-ASCII"));
            }
            out.write(vb);
        }
        return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        // 1. Derivation de la TAK depuis ZMK_VISA
        byte[] tak = decryptTak(TAK_SOUS_ZMK, ZMK_VISA);
        System.out.println("ZMK_VISA : "+ZMK_VISA+"  (KCV "+kcv(ISOUtil.hex2byte(ZMK_VISA))+")");
        System.out.println("TAK      : "+ISOUtil.hexString(tak).toUpperCase()+"  (KCV "+kcv(tak)+")");
        System.out.println("cible    : "+CIBLE+"\n");

        // 2. ISOMsg = SIGN-ON de CAM (1804/801)
        SwamPackager pkg=new SwamPackager();
        ISOMsg msg=new ISOMsg(); msg.setPackager(pkg); msg.setMTI("1804");
        msg.set(7,  "2607081558");
        msg.set(11, "760003");
        msg.set(12, "260708155824");
        msg.set(24, "811");
        msg.set(33, "101010");
        msg.set(37, "618915260708");
        msg.set(48, "P16033XDDFB767EAD39E819D304A556CB1066EE");

        // 3. Buffer DEs bruts sans prefixes (recette M6 validee sur Nabil)
        byte[] buf = buildRaw(msg, true);
        System.out.println("buffer (ascii): "+new String(buf,"ISO-8859-1"));
        System.out.println("buffer (hex)  : "+ISOUtil.hexString(buf));
        System.out.println("buffer (len)  : "+buf.length+" octets\n");

        // 4. MAC Alg1-3DES
        String mac1 = ISOUtil.hexString(macAlg1_3des(tak, buf)).substring(0,8).toUpperCase();
        String mac2 = ISOUtil.hexString(macMail(tak, buf)).substring(0,8).toUpperCase();
        System.out.println("Alg1-3DES (16o) : "+mac1+"   cible "+CIBLE+"   "+(mac1.equalsIgnoreCase(CIBLE)?">>> MATCH !!!":"!!! NO MATCH"));
        System.out.println("Alg1-DES  (K1)  : "+mac2+"   cible "+CIBLE+"   "+(mac2.equalsIgnoreCase(CIBLE)?">>> MATCH !!!":"!!! NO MATCH"));
    }
}
