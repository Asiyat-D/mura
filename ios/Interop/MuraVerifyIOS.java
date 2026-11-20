import kz.mura.domain.DecimalCodec;
import kz.mura.domain.PasswordCipher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.regex.Pattern;
public class MuraVerifyIOS {
  public static void main(String[] args) throws Exception {
    String json=Files.readString(Path.of(args[0]));
    var fields=new HashMap<String,String>();
    var matches=Pattern.compile("\"([^\"]+)\":\"([^\"]*)\"").matcher(json);
    while(matches.find()) fields.put(matches.group(1),matches.group(2));
    String text=new String(Base64.getDecoder().decode(fields.get("textBase64")),StandardCharsets.UTF_8);
    String password=new String(Base64.getDecoder().decode(fields.get("passwordBase64")),StandardCharsets.UTF_8);
    if(!text.equals(new DecimalCodec().decode(fields.get("encoded")))) throw new AssertionError("iOS decimal code mismatch");
    if(!text.equals(new PasswordCipher().decrypt(fields.get("encrypted"),password.toCharArray()))) throw new AssertionError("iOS AES-GCM envelope mismatch");
    System.out.println("PASS: iOS numeric code decoded by Android/JVM.");
    System.out.println("PASS: iOS MURA1 AES-256-GCM envelope decrypted by Android/JVM.");
  }
}
