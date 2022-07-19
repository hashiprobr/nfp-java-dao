package br.pro.hashi.nfp.dao;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.pro.hashi.nfp.dao.annotation.Autokey;
import br.pro.hashi.nfp.dao.annotation.File;
import br.pro.hashi.nfp.dao.annotation.Key;
import br.pro.hashi.nfp.dao.exception.BytecodeFirestoreException;
import br.pro.hashi.nfp.dao.exception.SourceFirestoreException;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.NotFoundException;

class Source {
	private final String typeName;
	private final boolean auto;
	private final Field keyField;
	private final Map<String, Field> fileFields;
	private final Map<String, Class<?>> types;

	Source(Class<?> type) {
		String typeName = type.getName();

		try {
			type.getConstructor();
		} catch (NoSuchMethodException exception) {
			throw new SourceFirestoreException("Class %s must have a public no-argument constructor".formatted(typeName));
		}

		boolean auto = false;
		Field keyField = null;
		Map<String, Field> fileFields = new HashMap<>();

		for (Class<?> ancestor = type; !ancestor.equals(Object.class); ancestor = ancestor.getSuperclass()) {
			for (Field field : ancestor.getDeclaredFields()) {
				String name = field.getName();
				if (field.isAnnotationPresent(Key.class)) {
					if (field.isAnnotationPresent(Autokey.class)) {
						throw new SourceFirestoreException("Field %s of class %s cannot be both a key and an autokey".formatted(name, typeName));
					} else {
						if (keyField == null) {
							keyField = field;
						} else {
							if (auto) {
								throw new SourceFirestoreException("Class %s cannot have both an autokey and a key".formatted(typeName));
							} else {
								throw new SourceFirestoreException("Class %s cannot have more than one key".formatted(typeName));
							}
						}
					}
				} else {
					if (field.isAnnotationPresent(Autokey.class)) {
						if (keyField == null) {
							if (!field.getType().equals(String.class)) {
								throw new SourceFirestoreException("Autokey %s of class %s must be a string".formatted(name, typeName));
							}
							auto = true;
							keyField = field;
						} else {
							if (auto) {
								throw new SourceFirestoreException("Class %s cannot have more than one autokey".formatted(typeName));
							} else {
								throw new SourceFirestoreException("Class %s cannot have both a key and an autokey".formatted(typeName));
							}
						}
					}
				}
				if (field.isAnnotationPresent(File.class)) {
					if (!field.getType().equals(String.class)) {
						throw new SourceFirestoreException("File %s of class %s must be a string".formatted(name, typeName));
					}
					fileFields.put(name, field);
					field.setAccessible(true);
				}
			}
		}

		if (keyField == null) {
			throw new SourceFirestoreException("Class %s must have either a key or an autokey".formatted(typeName));
		}
		keyField.setAccessible(true);

		this.typeName = typeName;
		this.auto = auto;
		this.keyField = keyField;
		this.fileFields = fileFields;
		this.types = new HashMap<>();
	}

	boolean isAuto() {
		return auto;
	}

	Field getKeyField() {
		return keyField;
	}

	Map<String, Field> getFileFields() {
		return fileFields;
	}

	Class<?> compile(String adapterName) {
		Class<?> type = types.get(adapterName);
		if (type == null) {
			ClassPool pool = ClassPool.getDefault();

			Lookup lookup = MethodHandles.lookup();
			String packageName = lookup.lookupClass().getPackageName();

			CtClass ctObject, ctSuper, ctType, ctAdapter;

			try {
				ctObject = pool.get("java.lang.Object");
				ctSuper = pool.get("%s.Adapter".formatted(packageName));
				ctType = pool.get(typeName);
				ctAdapter = pool.get(adapterName);
				for (CtClass ancestor = ctAdapter; !ancestor.equals(ctSuper); ancestor = ancestor.getSuperclass()) {
					if (ancestor.getDeclaredFields().length > 0) {
						throw new SourceFirestoreException("Class %s cannot have fields".formatted(adapterName));
					}
				}
			} catch (NotFoundException exception) {
				throw new BytecodeFirestoreException(exception);
			}

			String uuid = UUID.randomUUID().toString().replace("-", "");
			String proxyName = "%s.Proxy%s".formatted(packageName, uuid);

			try {
				CtClass ctProxy = pool.makeClass(proxyName);
				ctProxy.setModifiers(Modifier.PUBLIC);
				ctProxy.setSuperclass(ctSuper);

				CtConstructor ctConstructor;

				ctConstructor = new CtConstructor(null, ctProxy);
				ctConstructor.setModifiers(Modifier.PUBLIC);
				ctConstructor.setBody("$0.that = new %s();".formatted(typeName));
				ctProxy.addConstructor(ctConstructor);

				ctConstructor = new CtConstructor(new CtClass[] { ctType }, ctProxy);
				ctConstructor.setModifiers(Modifier.PUBLIC);
				ctConstructor.setBody("$0.that = $1;");
				ctProxy.addConstructor(ctConstructor);

				Set<String> methodNames = new HashSet<>();

				for (CtMethod ctAdapterMethod : ctAdapter.getMethods()) {
					int modifiers = ctAdapterMethod.getModifiers();
					CtClass declaring = ctAdapterMethod.getDeclaringClass();
					if (Modifier.isPublic(modifiers) && !declaring.equals(ctObject)) {
						String methodName = ctAdapterMethod.getName();
						methodNames.add(methodName);
						CtMethod ctMethod = new CtMethod(ctAdapterMethod, ctProxy, null);
						ctProxy.addMethod(ctMethod);
					}
				}

				for (CtMethod ctTypeMethod : ctType.getMethods()) {
					int modifiers = ctTypeMethod.getModifiers();
					CtClass declaring = ctTypeMethod.getDeclaringClass();
					if (Modifier.isPublic(modifiers) && !declaring.equals(ctObject)) {
						String methodName = ctTypeMethod.getName();
						if (!methodNames.contains(methodName)) {
							CtMethod ctMethod = new CtMethod(ctTypeMethod, ctProxy, null);
							if (ctTypeMethod.getReturnType().equals(CtClass.voidType)) {
								ctMethod.setBody("((%s) that).%s($$);".formatted(typeName, methodName));
							} else {
								ctMethod.setBody("return ((%s) that).%s($$);".formatted(typeName, methodName));
							}
							ctProxy.addMethod(ctMethod);
						}
					}
				}

				type = ctProxy.toClass(lookup);
			} catch (CannotCompileException exception) {
				throw new BytecodeFirestoreException(exception);
			} catch (NotFoundException exception) {
				throw new BytecodeFirestoreException(exception);
			}
			types.put(adapterName, type);
		}
		return type;
	}
}
