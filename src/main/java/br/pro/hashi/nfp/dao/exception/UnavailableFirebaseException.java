package br.pro.hashi.nfp.dao.exception;

public class UnavailableFirebaseException extends FirebaseException {
	private static final long serialVersionUID = 1932145222844859419L;

	public UnavailableFirebaseException(String message) {
		super(message);
	}
}
