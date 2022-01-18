package br.pro.hashi.nfp.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;

import br.pro.hashi.nfp.dao.exception.FirebaseException;
import br.pro.hashi.nfp.dao.exception.IOFirebaseException;

public class Firebase {
	private static Firebase defaultInstance = null;
	private static final Map<String, Firebase> instances = new HashMap<>();

	public static Firebase buildInstance(String path, String url, String name) {
		if (path == null) {
			throw new FirebaseException("Path cannot be null");
		}
		if (path.isBlank()) {
			throw new FirebaseException("Path cannot be blank");
		}
		if (url == null) {
			throw new FirebaseException("URL cannot be null");
		}
		if (url.isBlank()) {
			throw new FirebaseException("URL cannot be blank");
		}
		if (name == null) {
			if (defaultInstance == null) {
				return defaultInstance = new Firebase(path, url, name);
			} else {
				throw new FirebaseException("Already built a default Firebase instance");
			}
		} else {
			if (name.isBlank()) {
				throw new FirebaseException("Name cannot be blank");
			}
			if (instances.containsKey(name)) {
				throw new FirebaseException("Already built a Firebase instance named %s".formatted(name));
			} else {
				return instances.put(name, new Firebase(path, url, name));
			}
		}
	}

	public static Firebase buildInstance(String path, String url) {
		return buildInstance(path, url, null);
	}

	public static Firebase getInstance(String name) {
		if (name == null) {
			if (defaultInstance == null) {
				throw new FirebaseException("Did not build a default Firebase instance");
			} else {
				return defaultInstance;
			}
		} else {
			if (name.isBlank()) {
				throw new FirebaseException("Name cannot be blank");
			}
			if (instances.containsKey(name)) {
				return instances.get(name);
			} else {
				throw new FirebaseException("Did not build a Firebase instance named %s".formatted(name));
			}
		}
	}

	public static Firebase getInstance() {
		return getInstance(null);
	}

	private final String path;
	private final String url;
	private final String name;
	private FirebaseApp app;
	private Firestore firestore;
	private Bucket bucket;
	private boolean connected;
	private boolean exists;

	private Firebase(String path, String url, String name) {
		this.path = path;
		this.url = url;
		this.name = name;
		this.app = null;
		this.firestore = null;
		this.bucket = null;
		this.connected = false;
		this.exists = true;
	}

	private void checkExistence() {
		if (!exists) {
			throw new FirebaseException("This Firebase instance has been deleted");
		}
	}

	private void checkConnection() {
		if (!connected) {
			throw new FirebaseException("This Firebase instance is not connected");
		}
	}

	public void connect() {
		checkExistence();
		if (connected) {
			throw new FirebaseException("This Firebase instance is already connected");
		}

		System.out.println("Connecting Firebase instance...");

		GoogleCredentials credentials;
		try {
			FileInputStream stream = new FileInputStream(path);
			credentials = GoogleCredentials.fromStream(stream);
		} catch (IOException exception) {
			throw new IOFirebaseException(exception);
		}

		FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(credentials)
				.build();

		if (name == null) {
			app = FirebaseApp.initializeApp(options);
		} else {
			app = FirebaseApp.initializeApp(options, name);
		}

		firestore = FirestoreClient.getFirestore(app);

		bucket = StorageClient.getInstance(app).bucket(url);

		connected = true;

		System.out.println("Firebase instance connected");
	}

	public Firestore getFirestore() {
		checkExistence();
		checkConnection();
		return firestore;
	}

	public Bucket getBucket() {
		checkExistence();
		checkConnection();
		return bucket;
	}

	public void disconnect() {
		checkExistence();
		checkConnection();
		System.out.println("Disconnecting Firebase instance...");
		app.delete();
		app = null;
		firestore = null;
		bucket = null;
		connected = false;
		System.out.println("Firebase instance disconnected");
	}

	public void delete() {
		if (!exists) {
			throw new FirebaseException("This Firebase instance has already been deleted");
		}
		if (connected) {
			throw new FirebaseException("This Firebase instance is still connected");
		}
		exists = false;
		if (name == null) {
			defaultInstance = null;
		} else {
			instances.remove(name);
		}
	}
}
