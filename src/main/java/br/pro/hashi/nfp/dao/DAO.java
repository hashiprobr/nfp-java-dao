package br.pro.hashi.nfp.dao;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import com.google.cloud.WriteChannel;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;

import br.pro.hashi.nfp.dao.exception.AccessFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExecutionFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExistenceFirestoreException;
import br.pro.hashi.nfp.dao.exception.ExistenceStorageException;
import br.pro.hashi.nfp.dao.exception.FormatFirestoreException;
import br.pro.hashi.nfp.dao.exception.FormatStorageException;
import br.pro.hashi.nfp.dao.exception.IOFirebaseException;
import br.pro.hashi.nfp.dao.exception.InterruptedFirestoreException;

public abstract class DAO<T> {
	private final String path;
	private final Class<T> type;
	private Field keyField;
	private boolean auto;
	private Map<String, Field> fileFields;
	private Firebase firebase;
	private Firestore firestore;
	private CollectionReference collection;
	private Bucket bucket;

	@SuppressWarnings("unchecked")
	protected DAO(String path) {
		if (path == null) {
			throw new FormatFirestoreException("Path cannot be null");
		}
		if (path.isBlank()) {
			throw new FormatFirestoreException("Path cannot be blank");
		}
		if (path.indexOf('/') != -1) {
			throw new FormatFirestoreException("Path cannot have slashes");
		}
		this.path = path;

		ParameterizedType genericType = (ParameterizedType) getClass().getGenericSuperclass();
		Type[] types = genericType.getActualTypeArguments();
		this.type = (Class<T>) types[0];

		String className = this.type.getName();

		try {
			this.type.getConstructor();
		} catch (NoSuchMethodException exception) {
			throw new FormatFirestoreException("Class %s must have a public no-argument constructor".formatted(className));
		}

		this.keyField = null;
		this.auto = false;
		this.fileFields = new HashMap<>();
		for (Field field : this.type.getDeclaredFields()) {
			String fieldName = field.getName();
			if (field.isAnnotationPresent(Key.class)) {
				if (field.isAnnotationPresent(Autokey.class)) {
					throw new FormatFirestoreException("Field %s of class %s cannot be both a key and an autokey".formatted(fieldName, className));
				} else {
					if (this.keyField == null) {
						this.keyField = field;
					} else {
						if (this.auto) {
							throw new FormatFirestoreException("Class %s cannot have both an autokey and a key".formatted(className));
						} else {
							throw new FormatFirestoreException("Class %s cannot have more than one key".formatted(className));
						}
					}
				}
			} else {
				if (field.isAnnotationPresent(Autokey.class)) {
					if (this.keyField == null) {
						if (field.getType() != String.class) {
							throw new FormatFirestoreException("Autokey %s of class %s must be a string".formatted(fieldName, className));
						}
						this.keyField = field;
						this.auto = true;
					} else {
						if (this.auto) {
							throw new FormatFirestoreException("Class %s cannot have more than one autokey".formatted(className));
						} else {
							throw new FormatFirestoreException("Class %s cannot have both a key and an autokey".formatted(className));
						}
					}
				}
			}
			if (field.isAnnotationPresent(File.class)) {
				if (field.getType() != String.class) {
					throw new FormatFirestoreException("File %s of class %s must be a string".formatted(fieldName, className));
				}
				field.setAccessible(true);
				this.fileFields.put(fieldName, field);
			}
		}

		if (this.keyField == null) {
			throw new FormatFirestoreException("Class %s must have either a key or an autokey".formatted(className));
		}
		this.keyField.setAccessible(true);

		this.firebase = null;
		this.firestore = null;
		this.collection = null;
		this.bucket = null;
	}

	private String convert(Object rawKey) {
		if (rawKey == null) {
			return null;
		} else {
			return rawKey.toString();
		}
	}

	private List<String> convert(List<?> rawKeys) {
		if (rawKeys == null) {
			return null;
		} else {
			List<String> keys = new ArrayList<>();
			for (Object rawKey : rawKeys) {
				keys.add(convert(rawKey));
			}
			return keys;
		}
	}

	private String get(Field field, T value) {
		Object rawKey;
		try {
			rawKey = field.get(value);
		} catch (IllegalAccessException exception) {
			throw new AccessFirestoreException(exception);
		}
		return convert(rawKey);
	}

	private void set(Field field, T value, String key) {
		try {
			field.set(value, key);
		} catch (IllegalAccessException exception) {
			throw new AccessFirestoreException(exception);
		}
	}

	private void refresh() {
		Firestore instance = firebase.getFirestore();
		if (firestore != instance) {
			firestore = instance;
			collection = firestore.collection(path);
		}
		bucket = firebase.getBucket();
	}

	private void checkRead(String key) {
		if (key == null) {
			throw new FormatFirestoreException("Key cannot be null");
		}
		if (key.isBlank()) {
			throw new FormatFirestoreException("Key cannot be blank");
		}
		if (key.indexOf('/') != -1) {
			throw new FormatFirestoreException("Key cannot have slashes");
		}
	}

	private void checkRead(List<String> keys) {
		if (keys == null) {
			throw new FormatFirestoreException("List of keys cannot be null");
		}
		if (keys.isEmpty()) {
			throw new FormatFirestoreException("List of keys cannot be empty");
		}
		for (String key : keys) {
			checkRead(key);
		}
	}

	private void checkWrite(T value) {
		if (value == null) {
			throw new FormatFirestoreException("Value cannot be null");
		}
	}

	private void checkIn(List<?> values) {
		if (values == null) {
			throw new FormatFirestoreException("List of values cannot be null");
		}
		if (values.isEmpty()) {
			throw new FormatFirestoreException("List of values cannot be empty");
		}
	}

	private void checkQuery(Selection selection) {
		if (selection == null) {
			throw new FormatFirestoreException("Selection cannot be null");
		}
	}

	private void checkFile(T value, String name) {
		Field field = fileFields.get(name);
		if (field == null) {
			throw new FormatStorageException("File %s does not exist".formatted(name));
		}
		if (get(field, value) != null) {
			throw new FormatStorageException("File %s must be null".formatted(name));
		}
	}

	private void checkFile(InputStream stream) {
		if (stream == null) {
			throw new FormatStorageException("Stream cannot be null");
		}
	}

	private String buildPath(String key, String name) {
		return "%s/%s/%s".formatted(path, key, name);
	}

	private DocumentReference preCreate(T value) {
		checkWrite(value);
		initialized();
		String key;
		DocumentReference document;
		if (auto) {
			if (get(keyField, value) != null) {
				throw new FormatFirestoreException("Key must be null");
			}
			document = collection.document();
			key = document.getId();
			set(keyField, value, key);
		} else {
			key = get(keyField, value);
			checkRead(key);
			document = collection.document(key);
			try {
				if (document.get().get().exists()) {
					throw new ExistenceFirestoreException("Key %s already exists".formatted(key));
				}
			} catch (ExecutionException exception) {
				throw new ExecutionFirestoreException(exception);
			} catch (InterruptedException exception) {
				throw new InterruptedFirestoreException(exception);
			}
		}
		return document;
	}

	private DocumentReference preUpdate(String key) {
		DocumentReference document = collection.document(key);
		try {
			if (!document.get().get().exists()) {
				throw new ExistenceFirestoreException("Key %s does not exist".formatted(key));
			}
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		return document;
	}

	private DocumentReference preUpdate(T value) {
		checkWrite(value);
		String key = get(keyField, value);
		checkRead(key);
		initialized();
		return preUpdate(key);
	}

	private DocumentReference preUpdate(Map<String, Object> values) {
		if (values == null) {
			throw new FormatFirestoreException("Map of values cannot be null");
		}
		for (String name : values.keySet()) {
			try {
				type.getDeclaredField(name);
			} catch (NoSuchFieldException exception) {
				throw new FormatFirestoreException("Map of values cannot have %s".formatted(name));
			}
		}
		String keyName = keyField.getName();
		if (!values.containsKey(keyName)) {
			throw new FormatFirestoreException("Map of values must have %s".formatted(keyName));
		}
		String key = convert(values.get(keyName));
		checkRead(key);
		initialized();
		return preUpdate(key);
	}

	private void postCreateOrUpdate(DocumentReference document, T value) {
		try {
			document.set(value).get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
	}

	private void postUpdate(DocumentReference document, Map<String, Object> values) {
		try {
			document.set(values).get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
	}

	@SuppressWarnings("unchecked")
	public <S extends DAO<T>> S using(FirebaseManager manager, String name) {
		if (name == null) {
			firebase = manager.get();
		} else {
			firebase = manager.get(name);
		}
		firebase.checkConnection();
		refresh();
		return (S) this;
	}

	public <S extends DAO<T>> S using(FirebaseManager manager) {
		return using(manager, null);
	}

	public <S extends DAO<T>> S using(String name) {
		return using(Firebase.Manager(), name);
	}

	@SuppressWarnings("unchecked")
	public <S extends DAO<T>> S initialized() {
		if (firebase == null) {
			return using(Firebase.Manager(), null);
		} else {
			firebase.checkExistence();
			firebase.checkConnection();
			refresh();
			return (S) this;
		}
	}

	public Selection select() {
		initialized();
		return new Selection(collection);
	}

	public Selection select(List<?> rawKeys) {
		List<String> keys = convert(rawKeys);
		checkRead(keys);
		initialized();
		return new Selection(collection.whereIn(FieldPath.documentId(), keys));
	}

	public Selection selectExcept(List<?> rawKeys) {
		List<String> keys = convert(rawKeys);
		checkRead(keys);
		initialized();
		return new Selection(collection.whereNotIn(FieldPath.documentId(), keys));
	}

	public Selection selectWhereIn(String key, List<?> values) {
		checkRead(key);
		checkIn(values);
		initialized();
		return new Selection(collection.whereIn(key, values));
	}

	public Selection selectWhereNotIn(String key, List<?> values) {
		checkRead(key);
		checkIn(values);
		initialized();
		return new Selection(collection.whereNotIn(key, values));
	}

	public Selection selectWhereEqualTo(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereEqualTo(key, value));
	}

	public Selection selectWhereNotEqualTo(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereNotEqualTo(key, value));
	}

	public Selection selectWhereLessThan(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereLessThan(key, value));
	}

	public Selection selectWhereLessThanOrEqualTo(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereLessThanOrEqualTo(key, value));
	}

	public Selection selectWhereGreaterThan(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereGreaterThan(key, value));
	}

	public Selection selectWhereGreaterThanOrEqualTo(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereGreaterThanOrEqualTo(key, value));
	}

	public Selection selectWhereContains(String key, Object value) {
		checkRead(key);
		initialized();
		return new Selection(collection.whereArrayContains(key, value));
	}

	public Selection selectWhereContainsAny(String key, List<?> values) {
		checkRead(key);
		checkIn(values);
		initialized();
		return new Selection(collection.whereArrayContainsAny(key, values));
	}

	public void create(T value, Map<String, InputStream> streams) {
		DocumentReference document = preCreate(value);
		String key = document.getId();
		InputStream stream;
		String blobPath;
		for (String name : streams.keySet()) {
			checkFile(value, name);
			stream = streams.get(name);
			checkFile(stream);
			blobPath = buildPath(key, name);
			if (bucket.get(blobPath) != null) {
				throw new ExistenceStorageException("Path %s already exists".formatted(blobPath));
			}
		}
		for (String name : streams.keySet()) {
			Field field = fileFields.get(name);
			stream = streams.get(name);
			blobPath = buildPath(key, name);
			Blob blob = bucket.create(blobPath, stream);
			blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
			String url = blob.getMediaLink();
			set(field, value, url);
		}
		postCreateOrUpdate(document, value);
	}

	public void create(T value) {
		DocumentReference document = preCreate(value);
		postCreateOrUpdate(document, value);
	}

	public T retrieve(Object rawKey) {
		String key = convert(rawKey);
		checkRead(key);
		initialized();
		DocumentSnapshot document;
		try {
			document = collection.document(key).get().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		if (document.exists()) {
			return document.toObject(type);
		} else {
			return null;
		}
	}

	public List<T> retrieve(Selection selection) {
		checkQuery(selection);
		List<T> values = new ArrayList<>();
		for (DocumentSnapshot document : selection.getDocuments()) {
			values.add(document.toObject(type));
		}
		return values;
	}

	public void update(T value, Map<String, InputStream> streams) {
		DocumentReference document = preUpdate(value);
		String key = document.getId();
		InputStream stream;
		Field field;
		for (String name : streams.keySet()) {
			checkFile(value, name);
			stream = streams.get(name);
			checkFile(stream);
		}
		for (String name : streams.keySet()) {
			field = fileFields.get(name);
			stream = streams.get(name);
			String blobPath = buildPath(key, name);
			Blob blob = bucket.get(blobPath);
			if (blob == null) {
				blob = bucket.create(blobPath, stream);
				blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
			} else {
				WriteChannel writer = blob.writer();
				try {
					ByteBuffer source = ByteBuffer.wrap(stream.readAllBytes());
					writer.write(source);
					writer.close();
				} catch (IOException exception) {
					throw new IOFirebaseException(exception);
				}
				blob = bucket.get(blobPath);
			}
			String url = blob.getMediaLink();
			set(field, value, url);
		}
		List<String> blobPaths = new ArrayList<>();
		for (String name : fileFields.keySet()) {
			field = fileFields.get(name);
			String url = get(field, value);
			if (url == null) {
				blobPaths.add(buildPath(key, name));
			}
		}
		if (!blobPaths.isEmpty()) {
			for (Blob blob : bucket.get(blobPaths)) {
				if (blob != null) {
					blob.delete();
				}
			}
		}
		postCreateOrUpdate(document, value);
	}

	public void update(T value) {
		DocumentReference document = preUpdate(value);
		postCreateOrUpdate(document, value);
	}

	public void update(Map<String, Object> values, Map<String, InputStream> streams) {
		DocumentReference document = preUpdate(values);
		String key = document.getId();
		InputStream stream;
		for (String name : streams.keySet()) {
			if (fileFields.get(name) == null) {
				throw new FormatStorageException("File %s does not exist".formatted(name));
			}
			if (values.containsKey(name)) {
				throw new FormatStorageException("Map of values cannot have %s".formatted(name));
			}
			stream = streams.get(name);
			checkFile(stream);
		}
		for (String name : streams.keySet()) {
			stream = streams.get(name);
			String blobPath = buildPath(key, name);
			Blob blob = bucket.get(blobPath);
			if (blob == null) {
				blob = bucket.create(blobPath, stream);
				blob.createAcl(Acl.of(Acl.User.ofAllUsers(), Acl.Role.READER));
			} else {
				WriteChannel writer = blob.writer();
				try {
					ByteBuffer source = ByteBuffer.wrap(stream.readAllBytes());
					writer.write(source);
					writer.close();
				} catch (IOException exception) {
					throw new IOFirebaseException(exception);
				}
				blob = bucket.get(blobPath);
			}
			String url = blob.getMediaLink();
			values.put(name, url);
		}
		List<String> blobPaths = new ArrayList<>();
		for (String name : fileFields.keySet()) {
			if (values.containsKey(name)) {
				if (values.get(name) == null) {
					blobPaths.add(buildPath(key, name));
				}
			}
		}
		if (!blobPaths.isEmpty()) {
			for (Blob blob : bucket.get(blobPaths)) {
				if (blob != null) {
					blob.delete();
				}
			}
		}
		postUpdate(document, values);
	}

	public void update(Map<String, Object> values) {
		DocumentReference document = preUpdate(values);
		postUpdate(document, values);
	}

	public void delete(Object rawKey) {
		String key = convert(rawKey);
		checkRead(key);
		initialized();
		try {
			collection.document(key).delete().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		List<String> blobPaths = new ArrayList<>();
		for (String name : fileFields.keySet()) {
			blobPaths.add(buildPath(key, name));
		}
		if (!blobPaths.isEmpty()) {
			for (Blob blob : bucket.get(blobPaths)) {
				if (blob != null) {
					blob.delete();
				}
			}
		}
	}

	public void delete(Selection selection) {
		checkQuery(selection);
		WriteBatch batch = firestore.batch();
		List<String> blobPaths = new ArrayList<>();
		for (DocumentSnapshot document : selection.getDocuments()) {
			batch.delete(document.getReference());
			String key = document.getId();
			for (String name : fileFields.keySet()) {
				blobPaths.add(buildPath(key, name));
			}
		}
		try {
			batch.commit().get();
		} catch (ExecutionException exception) {
			throw new ExecutionFirestoreException(exception);
		} catch (InterruptedException exception) {
			throw new InterruptedFirestoreException(exception);
		}
		if (!blobPaths.isEmpty()) {
			for (Blob blob : bucket.get(blobPaths)) {
				if (blob != null) {
					blob.delete();
				}
			}
		}
	}
}
