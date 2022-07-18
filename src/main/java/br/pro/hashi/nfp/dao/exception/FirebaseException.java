package br.pro.hashi.nfp.dao.exception;

public abstract class FirebaseException extends RuntimeException {
	private static final long serialVersionUID = 7466478712904032861L;

	protected FirebaseException(Exception exception) {
		super(exception);
	}

	protected FirebaseException(String message) {
		super(message);
	}
}
