package br.pro.hashi.nfp.dao.exception;

import java.io.IOException;

public class CredentialsFirebaseException extends FirebaseException {
	private static final long serialVersionUID = -3082224498425143078L;

	public CredentialsFirebaseException(IOException exception) {
		super(exception);
	}
}
