package com.staging.sg.common.iso.crypto;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOUtil;
import com.staging.sg.common.iso.SwamPackager;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import java.util.Arrays;

/**
 * Classe GLOBALE de recette MAC SWAM entrant.
 * Teste toutes les combinaisons : TAK + ZMK, avec/sans LLVAR, simple/chaine (4o/8o).
 * Toutes les constructions de buffer partent de l'ISOMsg.
 *
 * Recette validee (prouvee) :
 *   - TAK_VISA, Alg1-3DES, DEs bruts avec prefixes LLVAR, simple    -> 9AA02ED9 / E847C263
 *   - TAK_VISA, Alg1-3DES, DEs bruts avec prefixes LLVAR, chaine 4o -> D9AC008B
 */
public class SwamMacEntrantTest {

    static final String ZMK_VISA     = "13AED5DA1F32347523C708C11F2608FD"; // KCV 2D617C
    static final String TAK_SOUS_ZMK = "E3C8EF7C4EEF54D81CE43CE2BCF33E37";

    static byte[] k3(byte[] k){ byte[] r=new byte[24]; System.arraycopy(k,0,r,0,16); System.arraycopy(k,0,r,16,8); return r; }
    static String kcv(byte[] k) throws Exception {
        Cipher c=Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k3(k),"DESede"));
        return ISOUtil.hexString(Arrays.copyOfRange(c.doFinal(new byte[8]),0,3)).toUpperCase();
    }
    static byte[] zeroPad(byte[] d){ int r=d.length%8; if(r==0&&d.length>0)return d; return Arrays.copyOf(d,d.length+(8-r)); }

    static byte[] decryptTak(String takHex, String zmkHex) throws Exception {
        Cipher c=Cipher.getInstance("DESede/ECB/NoPadding");
        c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(k3(ISOUtil.hex2byte(zmkHex)),"DESede"));
        return c.doFinal(ISOUtil.hex2byte(takHex));
    }

    static byte[] macAlg1_3des(byte[] key16, byte[] data) throws Exception {
        byte[] p=zeroPad(data);
        Cipher c=Cipher.getInstance("DESede/CBC/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(k3(key16),"DESede"),new IvParameterSpec(new byte[8]));
        byte[] e=c.doFinal(p); return Arrays.copyOfRange(e,e.length-8,e.length);
    }

    static boolean isLLVAR(int f){ switch(f){case 2:case 32:case 33:case 35:case 43:case 45:case 53:case 56:case 93:case 94:case 100:case 101:case 102:case 103:return true;default:return false;} }
    static boolean isLLLVAR(int f){ switch(f){case 46:case 48:case 54:case 55:case 60:case 61:case 62:case 123:case 127:return true;default:return false;} }

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

    static void showMac(String label, byte[] buf, byte[] key, String cible) throws Exception {
        String mac8=ISOUtil.hexString(macAlg1_3des(key,buf)).toUpperCase();
        String mac4=mac8.substring(0,8);
        System.out.println("  ["+label+"]");
        System.out.println("    buf(len="+buf.length+") : "+new String(buf,"ISO-8859-1"));
        System.out.println("    DE128(4o) : "+mac4+"   cible "+cible+"  "
                +(mac4.equalsIgnoreCase(cible)?">>> MATCH !!!":""));
    }

    /** Teste un message avec TAK et ZMK, toutes combinaisons. */
    static void testAll(ISOMsg msg, String cible, byte[] tak, byte[] zmk) throws Exception {
        System.out.println("=== AVEC TAK (KCV "+kcv(tak)+") ===");
        testCle(msg, cible, tak, "TAK");
        System.out.println("=== AVEC ZMK (KCV "+kcv(zmk)+") ===");
        testCle(msg, cible, zmk, "ZMK");
    }

    static void testCle(ISOMsg msg, String cible, byte[] key, String klabel) throws Exception {
        // buffers
        byte[] bLL  = buildRaw(msg, true);   // avec LLVAR
        byte[] bNoLL = buildRaw(msg, false);  // sans LLVAR

        // calculs avec LLVAR
        byte[] mac1LL = macAlg1_3des(key, bLL);
        String m1LL4  = ISOUtil.hexString(Arrays.copyOfRange(mac1LL,0,4)).toUpperCase();
        String m1LL8  = ISOUtil.hexString(mac1LL).toUpperCase();
        showMac(klabel+" avec-LL simple", bLL, key, cible);

        byte[] b2LL4 = concat(bLL, m1LL4.getBytes("US-ASCII"));
        showMac(klabel+" avec-LL chaine-4o", b2LL4, key, cible);

        byte[] b2LL8 = concat(bLL, m1LL8.getBytes("US-ASCII"));
        showMac(klabel+" avec-LL chaine-8o", b2LL8, key, cible);

        // calculs sans LLVAR
        byte[] mac1NL = macAlg1_3des(key, bNoLL);
        String m1NL4  = ISOUtil.hexString(Arrays.copyOfRange(mac1NL,0,4)).toUpperCase();
        String m1NL8  = ISOUtil.hexString(mac1NL).toUpperCase();
        showMac(klabel+" sans-LL simple", bNoLL, key, cible);

        byte[] b2NL4 = concat(bNoLL, m1NL4.getBytes("US-ASCII"));
        showMac(klabel+" sans-LL chaine-4o", b2NL4, key, cible);

        byte[] b2NL8 = concat(bNoLL, m1NL8.getBytes("US-ASCII"));
        showMac(klabel+" sans-LL chaine-8o", b2NL8, key, cible);
    }

    static byte[] concat(byte[] a, byte[] b) throws Exception {
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        out.write(a); out.write(b); return out.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        byte[] tak = decryptTak(TAK_SOUS_ZMK, ZMK_VISA);
        byte[] zmk = ISOUtil.hex2byte(ZMK_VISA);
        System.out.println("ZMK_VISA : "+ZMK_VISA+"  (KCV "+kcv(zmk)+")");
        System.out.println("TAK      : "+ISOUtil.hexString(tak).toUpperCase()+"  (KCV "+kcv(tak)+")\n");

        SwamPackager pkg=new SwamPackager();

        // ============================================================
        // MESSAGE : accuse echo test Nabil (1814) - cible 86F046A5
        // ============================================================
        ISOMsg msg=new ISOMsg(); msg.setPackager(pkg); msg.setMTI("1814");
        msg.set(7,  "2607081607");
        msg.set(11, "096801");
        msg.set(12, "260708160758");
        msg.set(33, "101011");
        msg.set(37, "618916096801");
        msg.set(39, "800");

        System.out.println("########################################");
        System.out.println("# ACCUSE ECHO TEST (1814) cible 86F046A5");
        System.out.println("########################################");
        testAll(msg, "86F046A5", tak, zmk);
    }
}
