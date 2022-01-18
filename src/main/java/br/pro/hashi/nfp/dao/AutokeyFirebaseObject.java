package br.pro.hashi.nfp.dao;

public abstract class AutokeyFirebaseObject extends FirebaseObject {
	private String key;

	protected AutokeyFirebaseObject() {
		this.key = null;
	}

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	@Override
	public String key() {
		return getKey();
	}
}
