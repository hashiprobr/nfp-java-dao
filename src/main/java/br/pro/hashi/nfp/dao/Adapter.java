package br.pro.hashi.nfp.dao;

import com.google.cloud.firestore.annotation.Exclude;

public abstract class Adapter<T> {
	@Exclude
	public T that;
}
