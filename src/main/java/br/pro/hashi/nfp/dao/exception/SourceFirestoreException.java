package br.pro.hashi.nfp.dao.exception;

public class SourceFirestoreException extends FirestoreException {
	private static final long serialVersionUID = -8031271455675526795L;

	public SourceFirestoreException(String message) {
		super("Invalid Firestore source", message);
	}
}
