// Generic object pool for reducing allocations

use parking_lot::Mutex;
use std::collections::VecDeque;

pub struct Pool<T> {
    available: Mutex<VecDeque<T>>,
    factory: Box<dyn Fn() -> T + Send + Sync>,
    max_size: usize,
}

impl<T> Pool<T> {
    pub fn new<F>(factory: F, max_size: usize) -> Self
    where
        F: Fn() -> T + Send + Sync + 'static,
    {
        Self {
            available: Mutex::new(VecDeque::with_capacity(max_size)),
            factory: Box::new(factory),
            max_size,
        }
    }
    
    pub fn acquire(&self) -> T {
        self.available.lock().pop_front().unwrap_or_else(|| (self.factory)())
    }
    
    pub fn release(&self, item: T) {
        let mut pool = self.available.lock();
        if pool.len() < self.max_size {
            pool.push_back(item);
        }
    }
}
