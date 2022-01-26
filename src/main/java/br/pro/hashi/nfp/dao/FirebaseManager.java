package br.pro.hashi.nfp.dao;

public interface FirebaseManager {
	boolean contains(String name);

	default boolean contains() {
		return contains(null);
	}

	Firebase create(String path, String url, String name);

	default Firebase create(String path, String url) {
		return create(path, url, null);
	}

	Firebase get(String name);

	default Firebase get() {
		return get(null);
	}

	void remove(String name);

	default void remove() {
		remove(null);
	}
}
