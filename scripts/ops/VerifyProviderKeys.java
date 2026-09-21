import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

/** Runs the deployed crypto class without starting Spring or making network calls. */
class VerifyProviderKeys {
    static class CryptoLoader extends ClassLoader {
        Class<?> loadCrypto(byte[] bytes) {
            return defineClass("com.interview.service.ApiKeyCryptoService", bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) {
        try (var jar = new JarFile("/app/app.jar")) {
            var entry = jar.getJarEntry("BOOT-INF/classes/com/interview/service/ApiKeyCryptoService.class");
            Class<?> type = new CryptoLoader().loadCrypto(jar.getInputStream(entry).readAllBytes());
            var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            Object crypto = type.getConstructor(String.class).newInstance(input.readLine());
            Object wrong = type.getConstructor(String.class).newInstance("restore-negative-control-" + java.util.UUID.randomUUID());
            var decrypt = type.getMethod("decrypt", String.class);
            int count = 0;
            String ciphertext;
            while ((ciphertext = input.readLine()) != null) {
                String value = (String) decrypt.invoke(crypto, ciphertext);
                if (value == null || value.isBlank()) throw new IllegalStateException();
                try {
                    decrypt.invoke(wrong, ciphertext);
                    throw new IllegalStateException("Wrong key accepted");
                } catch (InvocationTargetException expected) {
                    // Authentication failure with the wrong key is the required negative control.
                }
                count++;
            }
            System.out.println("{\"decrypted\":" + count + ",\"negative_control\":" + (count > 0) + "}");
        } catch (Exception error) {
            System.err.println("Offline provider-key verification failed; secret details suppressed");
            System.exit(1);
        }
    }
}
