package run.halo.alist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/8
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AListProperties {
    private String site;
    private String path;
    /**
     * Secret name.
     */
    private String secretName;

    public String getTokenKey(){
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        byte[] hash = digest.digest((site + secretName).getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
