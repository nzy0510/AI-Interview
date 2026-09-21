import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Shares the offline Qdrant container's network namespace; never uses a host port. */
class ReadQdrant {
    public static void main(String[] args) {
        try {
            if (!args[0].startsWith("/collections/restore_atoms")) throw new IllegalArgumentException();
            byte[] body = System.in.readAllBytes();
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:6333" + args[0]))
                    .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
            if (body.length > 0) request.POST(HttpRequest.BodyPublishers.ofByteArray(body));
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException();
            System.out.print(response.body());
        } catch (Exception error) {
            System.err.println("Isolated Qdrant probe failed");
            System.exit(1);
        }
    }
}
