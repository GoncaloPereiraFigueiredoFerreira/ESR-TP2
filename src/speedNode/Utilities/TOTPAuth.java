package speedNode.Utilities;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class TOTPAuth {

    private String password;
    MessageDigest md ;


    public TOTPAuth(String password){
        this.password = password;
        try {
            this.md = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public byte[] encryptMessage() {
        long seconds = System.currentTimeMillis()/1000;
        System.out.println("Seconds: "+seconds);
        System.out.println("Encrypted: " + Arrays.toString(returnTOTP(seconds)));
        return md.digest(returnTOTP(seconds));
    }

    private byte[] returnTOTP(long seconds){
        return (this.password + seconds).getBytes(StandardCharsets.UTF_8);
    }



    public boolean validateMessage(byte[] message){
        boolean flag = false;
        long seconds = System.currentTimeMillis()/1000;
        int secCounter=0;
        while (!flag && secCounter<30){
            seconds = seconds - secCounter;
            if (Arrays.equals(md.digest(returnTOTP(seconds)),message)){
                System.out.println("Passwords Matched");
                flag=true;
            }
            else secCounter++;
        }
        return flag;
    }



}
