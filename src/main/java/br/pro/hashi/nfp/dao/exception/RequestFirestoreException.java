package br.pro.hashi.nfp.dao.exception;

public class RequestFirestoreException extends FirestoreException {
	private static final long serialVersionUID = 5073606291970208405L;

	public RequestFirestoreException(String message) {
		super("Invalid Firestore request", message);
	}
}
