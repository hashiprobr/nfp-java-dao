package br.pro.hashi.nfp.dao.exception;

public class AccessFirestoreException extends FirestoreException {
	private static final long serialVersionUID = -8465974121133044067L;

	public AccessFirestoreException(IllegalAccessException exception) {
		super("Illegal Firestore access", exception);
	}
}
