package br.pro.hashi.nfp.dao;

public abstract class AutokeyFirebaseObject extends FirebaseObject {
	private String key;

	@Override
	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}
}
