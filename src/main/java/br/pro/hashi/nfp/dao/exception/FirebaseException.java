package br.pro.hashi.nfp.dao.exception;

public class FirebaseException extends RuntimeException {
	private static final long serialVersionUID = -5417296606275646337L;

	public FirebaseException(Exception exception) {
		super(exception);
	}

	public FirebaseException(String message) {
		super(message);
	}
}
