package br.pro.hashi.nfp.dao;

import java.util.concurrent.ExecutionException;

import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.Query.Direction;
import com.google.cloud.firestore.QuerySnapshot;

import br.pro.hashi.nfp.dao.exception.ExecutionFirestoreException;
import br.pro.hashi.nfp.dao.exception.FormatFirestoreException;
import br.pro.hashi.nfp.dao.exception.InterruptedFirestoreException;

public class Selection {
	private final Query query;
	private String orderBy;
	private boolean descending;
	private int offset;
	private int limit;

	Selection(Query query) {
		this.query = query;
		this.orderBy = null;
		this.descending = false;
		this.offset = 0;
		this.limit = 0;
	}

	QuerySnapshot getDocuments() {
		Query query = this.query;
		if (orderBy != null) {
			if (descending) {
				query = query.orderBy(orderBy, Direction.DESCENDING);
			} else {
				query = query.orderBy(orderBy);
			}
		}
		if (offset > 0) {
			query = query.offset(offset);
		}
		if (limit > 0) {
			query = query.limit(limit);
		}
		QuerySnapshot documents;
		try {
			documents = query.get().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		return documents;
	}

	public Selection orderBy(String orderBy) {
		if (orderBy == null) {
			throw new FormatFirestoreException("Order key cannot be null");
		}
		if (orderBy.isBlank()) {
			throw new FormatFirestoreException("Order key cannot be blank");
		}
		this.orderBy = orderBy;
		return this;
	}

	public Selection descending() {
		this.descending = true;
		return this;
	}

	public Selection offset(int offset) {
		if (offset < 1) {
			throw new FormatFirestoreException("Offset must be positive");
		}
		this.offset = offset;
		return this;
	}

	public Selection limit(int limit) {
		if (limit < 1) {
			throw new FormatFirestoreException("Limit must be positive");
		}
		this.limit = limit;
		return this;
	}
}
