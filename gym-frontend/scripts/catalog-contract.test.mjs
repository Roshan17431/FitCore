import assert from 'node:assert/strict';
import { test } from 'node:test';
import { workoutPayload, exercisePayload, nutritionPayload, approvedExerciseImage, exerciseVisualNote } from '../src/utils/catalog.js';

test('metadata-only workout edits omit selections and preserve legacy publication', () => {
  const payload = workoutPayload({ level: ' Legacy ', duration: ' 4 weeks ', schedule: ' Monday\n Friday ', exerciseIds: [8], status: 'PUBLISHED' }, { id: 1 }, false);
  assert.deepEqual(payload, { level: 'Legacy', duration: '4 weeks', schedule: ['Monday', 'Friday'], status: 'PUBLISHED' });
});
test('new workouts default to drafts and explicit clearing remains intentional', () => {
  assert.equal(workoutPayload({ level: 'A', duration: 'B', schedule: '', exerciseIds: [] }, null, false).status, 'DRAFT');
  assert.deepEqual(workoutPayload({ level: 'A', duration: 'B', schedule: '', exerciseIds: [], status: 'DRAFT' }, { id: 1 }, true).exerciseIds, []);
  assert.throws(() => workoutPayload({ level: 'A', duration: 'B', schedule: '', exerciseIds: [], status: 'PUBLISHED' }, { id: 1 }, true), /draft/i);
});
test('collection and field limits reject invalid data without silent truncation', () => {
  assert.throws(() => workoutPayload({ level: 'A', duration: 'B', schedule: 'x'.repeat(256), exerciseIds: [] }), /255/);
  assert.throws(() => nutritionPayload({ category: 'A', calories: -1, protein: 1, carbs: 2, fats: 3, meals: '' }), /calories/i);
  assert.throws(() => nutritionPayload({ category: 'A', calories: 1, protein: 1, carbs: 2, fats: 3, meals: Array(32).fill('Meal').join('\n') }), /31/);
});
test('payloads whitelist editable fields and trim optional image reference', () => {
  const exercise = exercisePayload({ name: ' Press ', category: ' Chest ', difficulty: 'BEGINNER', recommendedSets: '3', recommendedReps: ' 10 ', description: ' Safe ', imageUrl: '', gymId: 'foreign', imageApproved: true });
  assert.deepEqual(exercise, { name: 'Press', category: 'Chest', difficulty: 'BEGINNER', recommendedSets: 3, recommendedReps: '10', description: 'Safe', imageUrl: '' });
  assert.deepEqual(nutritionPayload({ category: ' Balanced ', calories: '900', protein: '80', carbs: '70', fats: '30', meals: ' Oats\n Beans ', gymId: 'foreign' }), { category: 'Balanced', calories: 900, protein: 80, carbs: 70, fats: 30, meals: ['Oats', 'Beans'] });
});
test('legacy and unapproved media never become image requests', () => {
  assert.equal(approvedExerciseImage({ imageUrl: 'https://legacy.invalid/image.jpg' }), '');
  assert.equal(approvedExerciseImage({ imageUrl: '/images/press.png', imageApproved: false }), '');
  assert.equal(approvedExerciseImage({ imageUrl: '/images/press.png', imageApproved: true }), '/images/press.png');
  assert.equal(approvedExerciseImage({ imageUrl: 'https://reviewed.invalid/image.jpg', imageApproved: true }), 'https://reviewed.invalid/image.jpg');
  assert.equal(approvedExerciseImage({ imageUrl: 'javascript:alert(1)', imageApproved: true }), '');
});


test('nutrition values are whole-number estimates and multiline fields trim boundary whitespace', () => {
  assert.throws(() => nutritionPayload({ category: 'Balanced', calories: 900.5, protein: 30, carbs: 40, fats: 20, meals: '' }), /whole number/i);
  assert.deepEqual(nutritionPayload({ category: 'Balanced', calories: 900, protein: 30, carbs: 40, fats: 20, meals: '\n Oats\n Beans\n' }).meals, ['Oats', 'Beans']);
});
test('image references fit the existing 255 character storage bound', () => {
  assert.throws(() => exercisePayload({ name: 'Press', category: 'Strength', difficulty: 'BEGINNER', imageUrl: '/images/' + 'a'.repeat(250) + '.png' }), /255/);
});

test('exercise fallback notes safely display arbitrary names and preserve known technique hints', () => {
  for (const name of ['__proto__', 'constructor', 'toString', 'New gym exercise']) assert.equal(exerciseVisualNote(name), name);
  assert.equal(exerciseVisualNote('  Lat Pulldown '), 'Cable machine · overhead pull');
  assert.equal(exerciseVisualNote(undefined), undefined);
});
