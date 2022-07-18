package br.pro.hashi.nfp.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseOptions;

import br.pro.hashi.nfp.dao.exception.CredentialsFirebaseException;
import br.pro.hashi.nfp.dao.exception.UnavailableFirebaseException;

public class FirebaseManager {
	private final Map<Class<?>, Source> sources;
	private final Map<String, Firebase> instances;
	private Firebase instance;

	FirebaseManager() {
		this.sources = new HashMap<>();
		this.instances = new HashMap<>();
		this.instance = null;
	}

	private Firebase doGet(String id) {
		if (id == null) {
			throw new IllegalArgumentException("Firebase instance id cannot be null");
		}
		id = id.strip();
		if (id.isEmpty()) {
			throw new IllegalArgumentException("Firebase instance id cannot be blank");
		}
		Firebase firebase = instances.get(id);
		if (firebase == null) {
			throw new UnavailableFirebaseException("Firebase instance with id %s does not exist".formatted(id));
		}
		return firebase;
	}

	Source reflect(Class<?> type) {
		Source source = sources.get(type);
		if (source == null) {
			source = new Source(type);
			sources.put(type, source);
		}
		return source;
	}

	boolean contains(Firebase firebase) {
		String id = firebase.getId();
		return instances.get(id) == firebase;
	}

	void remove(Firebase firebase) {
		String id = firebase.getId();
		if (instances.get(id) == firebase) {
			instances.remove(id);
			if (instance == firebase) {
				Iterator<String> key = instances.keySet().iterator();
				if (key.hasNext()) {
					instance = instances.get(key.next());
				} else {
					instance = null;
				}
			}
		}
	}

	public Firebase getFromCredentials(String path) {
		if (path == null) {
			throw new IllegalArgumentException("Firebase credentials path cannot be null");
		}
		path = path.strip();
		if (path.isEmpty()) {
			throw new IllegalArgumentException("Firebase credentials path cannot be blank");
		}

		ServiceAccountCredentials credentials;
		try {
			FileInputStream stream = new FileInputStream(path);
			credentials = ServiceAccountCredentials.fromStream(stream);
		} catch (IOException exception) {
			throw new CredentialsFirebaseException(exception);
		}

		String id = credentials.getProjectId();

		Firebase firebase = instances.get(id);
		if (firebase == null) {
			FirebaseOptions options = FirebaseOptions.builder()
					.setCredentials(credentials)
					.build();
			firebase = new Firebase(this, options, id);
			instances.put(id, firebase);
			if (instance == null) {
				instance = firebase;
			}
		}
		return firebase;
	}

	public Firebase get(String id) {
		return doGet(id);
	}

	public Firebase get() {
		if (instance == null) {
			throw new UnavailableFirebaseException("Firebase instances do not exist");
		}
		return instance;
	}

	public void set(String id) {
		instance = doGet(id);
	}
}
