package br.pro.hashi.nfp.dao;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseOptions;

import br.pro.hashi.nfp.dao.exception.FirebaseException;
import br.pro.hashi.nfp.dao.exception.IOFirebaseException;

class FirebaseManagerImpl implements FirebaseManager {
	private Firebase defaultInstance;
	private final Map<String, Firebase> instances;

	FirebaseManagerImpl() {
		this.defaultInstance = null;
		this.instances = new HashMap<>();
	}

	private void checkBlank(String name) {
		if (name.isBlank()) {
			throw new FirebaseException("Name cannot be blank");
		}
	}

	private void checkExistence(boolean removed) {
		if (removed) {
			throw new FirebaseException("This Firebase instance has already been removed");
		}
	}

	private void checkConnection(Firebase firebase) {
		if (firebase.isConnected()) {
			throw new FirebaseException("This Firebase instance is still connected");
		}
	}

	@Override
	public boolean contains(String name) {
		if (name == null) {
			return defaultInstance != null;
		} else {
			return instances.containsKey(name);
		}
	}

	@Override
	public Firebase create(String path, String url, String name) {
		Firebase firebase;

		if (path == null) {
			throw new FirebaseException("Path cannot be null");
		}
		if (path.isBlank()) {
			throw new FirebaseException("Path cannot be blank");
		}

		GoogleCredentials credentials;
		try {
			FileInputStream stream = new FileInputStream(path);
			credentials = GoogleCredentials.fromStream(stream);
		} catch (IOException exception) {
			throw new IOFirebaseException(exception);
		}

		if (url == null) {
			throw new FirebaseException("URL cannot be null");
		}
		if (url.isBlank()) {
			throw new FirebaseException("URL cannot be blank");
		}

		FirebaseOptions options = FirebaseOptions.builder()
				.setCredentials(credentials)
				.build();

		if (name == null) {
			if (defaultInstance == null) {
				firebase = new Firebase(this, options, url, null);
				defaultInstance = firebase;
			} else {
				throw new FirebaseException("Already built a default Firebase instance");
			}
		} else {
			checkBlank(name);
			if (instances.containsKey(name)) {
				throw new FirebaseException("Already built a Firebase instance named %s".formatted(name));
			} else {
				firebase = new Firebase(this, options, url, name);
				instances.put(name, firebase);
			}
		}

		return firebase;
	}

	@Override
	public Firebase get(String name) {
		Firebase firebase;
		if (name == null) {
			if (defaultInstance == null) {
				throw new FirebaseException("A default Firebase instance does not exist");
			} else {
				firebase = defaultInstance;
			}
		} else {
			checkBlank(name);
			if (instances.containsKey(name)) {
				firebase = instances.get(name);
			} else {
				throw new FirebaseException("A Firebase instance named %s does not exist".formatted(name));
			}
		}
		return firebase;
	}

	@Override
	public void remove(String name) {
		if (name == null) {
			checkExistence(defaultInstance == null);
			checkConnection(defaultInstance);
			defaultInstance = null;
		} else {
			checkExistence(!instances.containsKey(name));
			checkConnection(instances.get(name));
			instances.remove(name);
		}
	}
}
