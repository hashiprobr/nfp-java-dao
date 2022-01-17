package br.pro.hashi.nfp.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

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
	private boolean connected;
	private FirebaseApp app;
	private Firestore firestore;
	private Bucket bucket;

	public Firebase(String path, String url, String name) {
		this.path = path;
		this.url = url;
		this.name = name;
		this.connected = false;
		this.app = null;
		this.firestore = null;
		this.bucket = null;
	}

	private void check() {
		if ((name == null && defaultInstance == null) || (name != null && !instances.containsKey(name))) {
			throw new FirebaseException("This Firebase instance has been deleted");
		}
		if (!connected) {
			throw new FirebaseException("This Firebase instance is not connected");
		}
	}

	private void doDisconnect() {
		bucket = null;
		firestore = null;
		app.delete();
		app = null;
		connected = false;
	}

	public void connect() {
		if (connected) {
			throw new FirebaseException("This Firebase instance is already connected");
		}
		connected = true;

		Logger logger = Logger.getLogger("br.pro.hashi.nfp.dao");

		logger.info("Connecting Firebase instance...");

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

		logger.info("Firebase instance connected!");
	}

	public void disconnect() {
		check();
		doDisconnect();
	}

	public Firestore getFirestore() {
		check();
		return firestore;
	}

	public Bucket getBucket() {
		check();
		return bucket;
	}

	public void delete() {
		if ((name == null && defaultInstance == null) || (name != null && !instances.containsKey(name))) {
			throw new FirebaseException("This Firebase instance has already been deleted");
		}
		if (connected) {
			doDisconnect();
		}
		if (name == null) {
			defaultInstance = null;
		} else {
			instances.remove(name);
		}
	}
}
