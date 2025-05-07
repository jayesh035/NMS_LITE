package Testing;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ParallelCredentialsCreationTest {


    public static void main(String[] args) throws Exception {
        int numRequests = 50;  // Number of parallel POST requests
        String url = "http://localhost:8080/credentials";  // <-- replace with your actual POST URL

        ExecutorService executor = Executors.newFixedThreadPool(10);  // Adjust thread pool size as needed
        HttpClient client = HttpClient.newHttpClient();

        for (int i = 0; i < numRequests; i++) {
            int id = i;
            executor.submit(() -> {
                try {
                    // You can vary payload slightly for realism
                    String jsonPayload = String.format("""
                           {
                           "credential_name": "dpi-123",
                                    "systemtype": "SSH",
                                    "data": {
                                        "community": "public",
                                        "description": "API key for service X"
                                    }
                           }
                           """, id);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.printf("Request %d → Status: %d, Response: %s%n", id, response.statusCode(), response.body());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.MINUTES);

    }
}