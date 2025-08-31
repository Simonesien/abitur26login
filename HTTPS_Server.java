import com.sun.net.httpserver.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.net.httpserver.HttpsServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONException;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.sql.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;

public class HTTPS_Server{
	public static class RequestException extends Exception{
		private int statusCode;
		public RequestException(String message, int statusCode){
			super(message);
			this.statusCode = statusCode;
		}
		public int getStatusCode(){
			return this.statusCode;
		}
	}

	public static class LoginHandler extends AbstractRequestHandler{
		private String url = "/login";

		@Override
		public String getUrl(){
			return this.url;
		}

		@Override
		public String[] answerRequest(JSONObject requestJson) throws RequestException{
			Set<String> actualKeys = new HashSet<>();
			requestJson.keys().forEachRemaining(actualKeys::add);
			if(!Set.of("Username", "Password").equals(actualKeys)){
				throw new RequestException("Wrong keys in JSON", 400);
			}
			actualKeys = null;

			if(!requestJson.getString("Username").matches("^[a-z0-9\u002d\u00e4\u00f6\u00fc\u00df.]+$")) throw new RequestException("Invalid characters in username", 400);
			if(requestJson.getString("Username").startsWith(".") || requestJson.getString("Username").endsWith(".")) throw new RequestException("No dot at the beginning or end in username", 400);
			if(requestJson.getString("Username").chars().filter(ch -> ch == '.').count() > 1) throw new RequestException("Not more than one dot in username", 400);
			if(requestJson.getString("Password").equals("")) throw new RequestException("No empty password", 400);
			int statusCode;
			try{
				HttpRequest.Builder httpRequestBuilder = HttpRequest.newBuilder();
				httpRequestBuilder.uri(URI.create("https://login.schulportal.hessen.de/?i=5125"));
				httpRequestBuilder.header("Cookie", "llnglanguage=de; schulportal_lastschool=5125; schulportal_logindomain=login.schulportal.hessen.de");
				httpRequestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36");
				httpRequestBuilder.header("Origin", "https://login.schulportal.hessen.de");
				httpRequestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
				httpRequestBuilder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
				httpRequestBuilder.POST(BodyPublishers.ofString("timezone=2&skin=sp&user2="+URLEncoder.encode(requestJson.getString("Username"), "UTF-8")+"&user=5125."+URLEncoder.encode(requestJson.getString("Username"), "UTF-8")+"&password="+URLEncoder.encode(requestJson.getString("Password"), "UTF-8")));

				statusCode = HttpClient.newHttpClient().send(httpRequestBuilder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
			}
			catch(IOException exception){
				throw new RequestException("Login data could not be verified", 500);
			}
			catch(InterruptedException exception){
				throw new RequestException("Login data could not be verified", 500);
			}
			if(statusCode == 302){
				byte[] sessionCookieBytes = new byte[32];
				new SecureRandom().nextBytes(sessionCookieBytes);
				String sessionCookie = Base64.getUrlEncoder().withoutPadding().encodeToString(sessionCookieBytes);

				Connection connection = null;
				PreparedStatement preparedStatement = null;
				try{
					connection = DriverManager.getConnection("jdbc:sqlite:login.db");
					preparedStatement = connection.prepareStatement("INSERT INTO Login(Timestamp, Username, SessionCookie) VALUES(?, ?, ?)");
					preparedStatement.setString(1, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
					preparedStatement.setString(2, requestJson.getString("Username"));
					preparedStatement.setString(3, sessionCookie);
					preparedStatement.executeUpdate();
				}
				catch(SQLException exception){
					System.out.println(exception);
					throw new RequestException("Login succeeded, but session cookie could not be stored", 500);
				}
				finally{
					if(connection != null){
						try{
							connection.close();
							preparedStatement.close();
						}
						catch(SQLException exception){
							System.out.println(exception);
						}
					}
				}

				return new String[] {"{\"Authenticated\": \"True\", \"SessionCookie\": \"" + sessionCookie + "\"}", requestJson.getString("Username"), "Logged in"};
			}
			else{
				return new String[] {"{\"Authenticated\": \"False\"}", requestJson.getString("Username"), "Wrong password for login"};
			}
		}
	}

	public static abstract class AbstractRequestHandler implements HttpHandler{
		public abstract String getUrl();
		public abstract String[] answerRequest(JSONObject requestJson) throws RequestException;

		@Override
		public void handle(HttpExchange exchange){
			String clientIP = null;
			String path = null;
			String userAgent = null;
			try{
				clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
				path = exchange.getRequestURI().getPath();
				userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
				if (userAgent == null){
					throw new RequestException("No User Agent in request", 400);
				}
				if(!this.getUrl().equals(path)){
					throw new RequestException("No additional URL after valid API Endpoint", 404);
				}
				if(!"POST".equalsIgnoreCase(exchange.getRequestMethod())){
					throw new RequestException("Wrong request method, use POST", 405);
				}

				InputStream inputStream = exchange.getRequestBody();
				byte[] dataBytes = inputStream.readAllBytes();
				String dataString = new String(dataBytes, StandardCharsets.UTF_8);
				if(dataString.equals("")){
					throw new RequestException("No content in request", 400);
				}

				JSONObject jsonObject = null;
				try{
					JSONTokener tokener = new JSONTokener(dataString);
					jsonObject = new JSONObject(tokener);
				}
				catch(JSONException exception){
					throw new RequestException("No valid JSON", 400);
				}

				String[] answeredRequest = answerRequest(jsonObject);

				exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
				exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
				exchange.getResponseHeaders().add("Connection", "close");
				exchange.getResponseHeaders().add("X-Clacks-Overhead", "GNU Terry Pratchett");
				exchange.sendResponseHeaders(200, answeredRequest[0].getBytes().length);
				exchange.getResponseBody().write(answeredRequest[0].getBytes());
				exchange.getResponseBody().close();

				Connection connection = null;
				PreparedStatement preparedStatement = null;
				try{
					connection = DriverManager.getConnection("jdbc:sqlite:login.db");
					preparedStatement = connection.prepareStatement("INSERT INTO Request(Timestamp, Path, ClientIP, UserAgent, User, Action) VALUES(?, ?, ?, ?, ?, ?)");
					preparedStatement.setString(1, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
					preparedStatement.setString(2, path);
					preparedStatement.setString(3, clientIP);
					preparedStatement.setString(4, userAgent);
					preparedStatement.setString(5, answeredRequest[1]);
					preparedStatement.setString(6, answeredRequest[2]);
					preparedStatement.executeUpdate();
				}
				catch(SQLException exception){
					System.out.println("Logging failed (within AbstractRequestHandler): " + exception.getMessage());
				}
				finally{
					if(connection != null){
						try{
							connection.close();
							preparedStatement.close();
						}
						catch(SQLException exception){
							System.out.println(exception);
						}
					}
				}
			}
			catch(RequestException exception){
				try{
					if (userAgent == null){
						exception = new RequestException("No User Agent in request", 400);
					}
					String Response = "{\"Error\": \"" + exception.getMessage() + "\"}";
					exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
					exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
					exchange.getResponseHeaders().add("Connection", "close");
					exchange.getResponseHeaders().add("X-Clacks-Overhead", "GNU Terry Pratchett");
					if(exception.getStatusCode() == 405) exchange.getResponseHeaders().add("Allow", "POST");
					exchange.sendResponseHeaders(exception.getStatusCode(), Response.getBytes().length);
					exchange.getResponseBody().write(Response.getBytes());
					exchange.getResponseBody().close();

					Connection connection = null;
					PreparedStatement preparedStatement = null;
					try{
						connection = DriverManager.getConnection("jdbc:sqlite:login.db");
						preparedStatement = connection.prepareStatement("INSERT INTO Request(Timestamp, Path, ClientIP, UserAgent, Error, StatusCode) VALUES(?, ?, ?, ?, ?, ?)");
						preparedStatement.setString(1, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
						preparedStatement.setString(2, path);
						preparedStatement.setString(3, clientIP);
						preparedStatement.setString(4, (userAgent == null ? "-" : userAgent));
						preparedStatement.setString(5, exception.getMessage());
						preparedStatement.setInt(6, exception.getStatusCode());
						preparedStatement.executeUpdate();
					}
					catch(SQLException sqlexception){
						System.out.println("Logging failed (within catched RequestException: " + sqlexception.getMessage());
					}
					finally{
						if(connection != null){
							try{
								connection.close();
								preparedStatement.close();
							}
							catch(SQLException sqlexception){
								System.out.println(exception);
							}
						}
					}
				}
				catch(IOException ioexception){
					System.out.println("Request failed (within already catched RequestException): " + ioexception.getMessage());
				}
			}
			catch(IOException exception){
				System.out.println("Request failed: " + exception.getMessage());
			}
		}
	}

	public static class DefaultHandler implements HttpHandler{
		@Override
		public void handle(HttpExchange exchange) throws IOException{
			try{
				String clientIP = exchange.getRemoteAddress().getAddress().getHostAddress();
				String path = exchange.getRequestURI().getPath();
				String userAgent = null;
				userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
				String Response = "{\"Error\": \"API Endpoint not found\"}";
				exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*"); //
				exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
				exchange.getResponseHeaders().add("Connection", "close");
				exchange.getResponseHeaders().add("X-Clacks-Overhead", "GNU Terry Pratchett");
				exchange.sendResponseHeaders(404, Response.getBytes().length);
				exchange.getResponseBody().write(Response.getBytes());
				exchange.getResponseBody().close();

				Connection connection = null;
				PreparedStatement preparedStatement = null;
				try{
					connection = DriverManager.getConnection("jdbc:sqlite:login.db");
					preparedStatement = connection.prepareStatement("INSERT INTO Request(Timestamp, Path, ClientIP, UserAgent, Error, StatusCode) VALUES(?, ?, ?, ?, ?, ?)");
					preparedStatement.setString(1, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
					preparedStatement.setString(2, path);
					preparedStatement.setString(3, clientIP);
					preparedStatement.setString(4, (userAgent == null ? "-" : userAgent));
					preparedStatement.setString(5, "API Endpoint not found");
					preparedStatement.setInt(6, 404);
					preparedStatement.executeUpdate();
				}
				catch(SQLException exception){
					System.out.println("Logging failed (within DefaultHandler): " + exception.getMessage());
				}
				finally{
					if(connection != null){
						try{
							connection.close();
							preparedStatement.close();
						}
						catch(SQLException exception){
							System.out.println(exception);
						}
					}
				}
			}
			catch(IOException exception){
				System.out.println("Request failed (within catched DefaultHandler): " + exception.getMessage());
			}
		}
	}

	public static void main(String[] args) throws Exception{
		try{
			HttpsServer HTTPS_Server = HttpsServer.create(new InetSocketAddress(45308), 0);

			KeyStore keyStore = KeyStore.getInstance("PKCS12");
			keyStore.load(new FileInputStream("abitur26.de-certificate.p12"), "".toCharArray());

			TrustManagerFactory Trust_Manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
			Trust_Manager.init(keyStore);

			KeyManagerFactory Key_Manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			Key_Manager.init(keyStore, "".toCharArray());

			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(Key_Manager.getKeyManagers(), Trust_Manager.getTrustManagers(), null);

			HTTPS_Server.setHttpsConfigurator(
				new HttpsConfigurator(sslContext){
					public void configure(HttpsParameters params){
						try{
							SSLContext sslContext = getSSLContext();
							SSLEngine sslEngine = sslContext.createSSLEngine();
							params.setNeedClientAuth(false);
							params.setCipherSuites(sslEngine.getEnabledCipherSuites());
							params.setProtocols(sslEngine.getEnabledProtocols());
							params.setSSLParameters(sslContext.getSupportedSSLParameters());
							System.out.println("Someone successfully connected via SSL");
						}
						catch(Exception exception){
							System.out.println("SSL failed");
							exception.printStackTrace();
						}
					}
				}
			);
			HTTPS_Server.createContext("/login", new LoginHandler());
			// Additional contexts aren't published here
			HTTPS_Server.createContext("/", new DefaultHandler());
			HTTPS_Server.setExecutor(Executors.newFixedThreadPool(10));
			HTTPS_Server.start();
			System.out.println("On");
		}
		catch(Exception exception){
			System.out.println("Failed to create HTTPS server on port 45308");
			exception.printStackTrace();
		}
	}
}
