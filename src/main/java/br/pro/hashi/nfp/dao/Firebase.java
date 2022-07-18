package br.pro.hashi.nfp.dao;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;

import br.pro.hashi.nfp.dao.exception.UnavailableFirebaseException;

public class Firebase {
	private static final FirebaseManager MANAGER = new FirebaseManager();

	public static FirebaseManager manager() {
		return MANAGER;
	}

	private final Logger logger;
	private final FirebaseManager manager;
	private final FirebaseOptions options;
	private final String id;
	private FirebaseApp app;
	private Firestore firestore;
	private Map<String, CollectionReference> collections;
	private Bucket bucket;

	Firebase(FirebaseManager manager, FirebaseOptions options, String id) {
		this.logger = LoggerFactory.getLogger(Firebase.class);
		this.manager = manager;
		this.options = options;
		this.id = id;
		this.app = null;
		this.firestore = null;
		this.collections = null;
		this.bucket = null;
	}

	String getId() {
		return id;
	}

	Firestore getFirestore() {
		return firestore;
	}

	Bucket getBucket() {
		return bucket;
	}

	Source reflect(Class<?> type) {
		return manager.reflect(type);
	}

	CollectionReference collection(String path) {
		CollectionReference collection = collections.get(path);
		if (collection == null) {
			collection = firestore.collection(path);
			collections.put(path, collection);
		}
		return collection;
	}

	public void connect() {
		if (!manager.contains(this)) {
			throw new UnavailableFirebaseException("Firebase instance has been deleted");
		}
		if (app != null) {
			return;
		}
		logger.info("Connecting Firebase instance...");
		app = FirebaseApp.initializeApp(options, id);
		String url = "%s.appspot.com".formatted(id);
		firestore = FirestoreClient.getFirestore(app);
		collections = new HashMap<>();
		bucket = StorageClient.getInstance(app).bucket(url);
		logger.info("Firebase instance connected to %s".formatted(id));
	}

	public void disconnect() {
		if (app == null) {
			return;
		}
		logger.info("Disconnecting Firebase instance from %s...".formatted(id));
		bucket = null;
		collections = null;
		firestore = null;
		app.delete();
		app = null;
		logger.info("Firebase instance disconnected");
	}

	public void delete() {
		disconnect();
		manager.remove(this);
	}
}
