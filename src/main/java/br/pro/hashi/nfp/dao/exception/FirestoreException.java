package br.pro.hashi.nfp.dao.exception;

public abstract class FirestoreException extends FirebaseException {
	private static final long serialVersionUID = -2427529033998002790L;

	protected FirestoreException(String prefix, Exception exception) {
		super("%s: %s".formatted(prefix, exception.getMessage()));
	}

	protected FirestoreException(String prefix, String message) {
		super("%s: %s".formatted(prefix, message));
	}
}
