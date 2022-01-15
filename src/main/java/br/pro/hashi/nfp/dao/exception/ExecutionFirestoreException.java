package br.pro.hashi.nfp.dao.exception;

import java.util.concurrent.ExecutionException;

public class ExecutionFirestoreException extends FirestoreException {
	private static final long serialVersionUID = 1829234000563155010L;

	public ExecutionFirestoreException(ExecutionException exception) {
		super("Firestore execution failed", exception);
	}
}
