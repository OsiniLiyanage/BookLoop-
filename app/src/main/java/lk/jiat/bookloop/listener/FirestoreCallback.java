package lk.jiat.bookloop.listener;

public interface FirestoreCallback<T> {
    void onCallback(T data);
}
