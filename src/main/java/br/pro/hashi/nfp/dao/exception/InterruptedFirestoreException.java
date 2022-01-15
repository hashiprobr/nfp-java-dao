package br.pro.hashi.nfp.dao.exception;

public class InterruptedFirestoreException extends FirestoreException {
	private static final long serialVersionUID = 5212713079934892628L;

	public InterruptedFirestoreException(InterruptedException exception) {
		super("Firestore execution interrupted", exception);
	}
}
