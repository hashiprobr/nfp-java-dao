package br.pro.hashi.nfp.dao;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Bucket;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Singleton;

import br.pro.hashi.nfp.dao.exception.FirebaseException;

public class Firebase {
	private static final Injector injector = Guice.createInjector(new AbstractModule() {
		@Override
		protected void configure() {
			bind(FirebaseManager.class).to(FirebaseManagerImpl.class).in(Singleton.class);
		}
	});

	public static FirebaseManager Manager() {
		return injector.getInstance(FirebaseManager.class);
	}

	private final FirebaseManager manager;
	private final FirebaseOptions options;
	private final String url;
	private final String name;
	private FirebaseApp app;
	private Firestore firestore;
	private Bucket bucket;
	private boolean connected;

	Firebase(FirebaseManager manager, FirebaseOptions options, String url, String name) {
		this.manager = manager;
		this.options = options;
		this.url = url;
		this.name = name;
		this.app = null;
		this.firestore = null;
		this.bucket = null;
		this.connected = false;
	}

	Firestore getFirestore() {
		return firestore;
	}

	Bucket getBucket() {
		return bucket;
	}

	boolean isConnected() {
		return connected;
	}

	void checkExistence() {
		if (!manager.contains(name)) {
			throw new FirebaseException("This Firebase instance has been removed");
		}
	}

	void checkConnection() {
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

	public void remove() {
		manager.remove(name);
	}
}
