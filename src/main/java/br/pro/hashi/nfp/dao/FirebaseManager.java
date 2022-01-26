package br.pro.hashi.nfp.dao;

public interface FirebaseManager {
	boolean contains(String name);

	default boolean contains() {
		return contains(null);
	}

	Firebase buildInstance(String path, String url, String name);

	default Firebase buildInstance(String path, String url) {
		return buildInstance(path, url, null);
	}

	Firebase getInstance(String name);

	default Firebase getInstance() {
		return getInstance(null);
	}

	void remove(String name);

	default void remove() {
		remove(null);
	}
}
