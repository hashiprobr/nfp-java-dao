package br.pro.hashi.nfp.dao.exception;

public abstract class StorageException extends FirebaseException {
	private static final long serialVersionUID = -2811733570671711763L;

	protected StorageException(String message) {
		super("Invalid storage input: %s".formatted(message));
	}
}
