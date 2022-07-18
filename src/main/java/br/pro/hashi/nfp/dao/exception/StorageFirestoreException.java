package br.pro.hashi.nfp.dao.exception;

import java.io.IOException;

public class StorageFirestoreException extends FirestoreException {
	private static final long serialVersionUID = 8339880677827306886L;

	public StorageFirestoreException(IOException exception) {
		super("Storage execution failed", exception);
	}
}
