package br.pro.hashi.nfp.dao.exception;

public abstract class InputFirestoreException extends FirestoreException {
	private static final long serialVersionUID = 4066758977223172799L;

	protected InputFirestoreException(String message) {
		super("Invalid Firestore input", message);
	}
}
