import { describe, expect, it } from 'vitest';
import {
  buildRunSpec, draftFromCatalog, draftFromSpec, errorsFor, humanize, isRange, newAttack, stepForField,
} from './runDraft';
import type { Catalog } from '../types/run';

const catalog: Catalog = {
  attacks: [
    {
      id: 'uc01_random_replay', uc: 'uc01', attackType: 'random_replay', description: null,
      template: { attackType: 'random_replay', enabled: true, delayMs: { min: 50, max: 500 }, reorderProb: 0.05 },
    },
    {
      id: 'uc08_grayhole', uc: 'uc08', attackType: 'grayhole', description: 'Selective dropping',
      template: { attackType: 'grayhole', description: 'Selective dropping', comment: 'docs', dropRate: { min: 0.25, max: 0.35 } },
    },
  ],
  electricalSources: ['res10', 'res50'],
  svStrategies: ['DECIMATED', 'UPSTREAM_FAITHFUL'],
  formats: ['csv', 'arff'],
  defaults: {
    generation: { numberOfMessages: 60000, faultProbability: 5, svStrategy: 'DECIMATED', svSamplesPerGoose: 80 },
    gooseFlow: { goID: 'IntLockA', ethSrc: '01:0c:cd:01:2f:80', ndsCom: true, numberOfMessages: 1000000 },
    setupIED: { iedName: 'Protection IED', minTime: '4', maxTime: '1000' },
    datasetStructure: { messagesPerSegment: 100000, includeBenignSegment: true },
  },
};

describe('draftFromCatalog', () => {
  it('takes defaults from the core and caps the paper-sized volumes', () => {
    const d = draftFromCatalog(catalog);
    expect(d.setupIED.iedName).toBe('Protection IED');
    expect(d.gooseFlow.ndsCom).toBe(true);
    expect(d.gooseFlow).not.toHaveProperty('numberOfMessages');
    expect(d.benign.numberOfMessages).toBe(10000);
    expect(d.datasetStructure.messagesPerSegment).toBe(1000);
    expect(d.benign.electricalSources).toBe('res10');
    expect(d.randomSeed).toBe(42);
  });

  it('works without a catalog (backend offline)', () => {
    const d = draftFromCatalog(null);
    expect(d.gooseFlow.ethType).toBe('0x88B8');
    expect(d.attacks).toEqual([]);
  });
});

describe('buildRunSpec', () => {
  it('produces the POST /api/runs body in the core schema', () => {
    const d = draftFromCatalog(catalog);
    d.name = '  my dataset ';
    d.format = 'arff';
    d.attacks = [newAttack(catalog.attacks[0]), newAttack(catalog.attacks[1])];
    const spec = buildRunSpec(d);

    expect(spec.name).toBe('my dataset');
    expect(spec.output).toEqual({ format: 'arff' });
    expect(spec.randomSeed).toBe(42);
    expect(spec.gooseFlow.goID).toBe('IntLockA');
    expect(spec.attacks).toHaveLength(2);
    expect(spec.attacks[0].config).toEqual({ attackType: 'random_replay', delayMs: { min: 50, max: 500 }, reorderProb: 0.05 });
    // documentation keys are not parameters
    expect(spec.attacks[1].config).toEqual({ attackType: 'grayhole', dropRate: { min: 0.25, max: 0.35 } });
  });

  it('does not share state between an attack and its template', () => {
    const a = newAttack(catalog.attacks[0]);
    (a.config.delayMs as { min: number }).min = 1;
    expect((catalog.attacks[0].template.delayMs as { min: number }).min).toBe(50);
  });

  it('round-trips through draftFromSpec', () => {
    const d = draftFromCatalog(catalog);
    d.attacks = [newAttack(catalog.attacks[1])];
    d.randomSeed = 7;
    const back = draftFromSpec(buildRunSpec(d), catalog);
    expect(back.randomSeed).toBe(7);
    expect(back.attacks[0].templateId).toBe('uc08_grayhole');
    expect(buildRunSpec(back)).toEqual(buildRunSpec(d));
  });
});

describe('error routing', () => {
  it('maps backend field paths to wizard steps', () => {
    expect(stepForField('setupIED.minTime')).toBe('ied');
    expect(stepForField('gooseFlow.ethSrc')).toBe('goose');
    expect(stepForField('benign.electricalSources')).toBe('benign');
    expect(stepForField('attacks[1].config.delayMs.max')).toBe('attacks');
    expect(stepForField('randomSeed')).toBe('generate');
    expect(stepForField('datasetStructure.messagesPerSegment')).toBe('generate');
  });

  it('filters errors by prefix', () => {
    const errs = [
      { field: 'attacks', message: 'Add at least one attack' },
      { field: 'attacks[0].config.x', message: 'bad' },
      { field: 'benign.numberOfMessages', message: 'Required' },
    ];
    expect(errorsFor(errs, 'attacks')).toHaveLength(2);
    expect(errorsFor(errs, 'benign')).toHaveLength(1);
  });
});

describe('labels', () => {
  it('humanizes template keys with units', () => {
    expect(humanize('delayMs')).toBe('Delay (ms)');
    expect(humanize('windowS')).toBe('Window (s)');
    expect(humanize('burstDropLen')).toBe('Burst drop len');
    expect(humanize('lambda')).toBe('Lambda');
  });

  it('recognizes {min,max} ranges only', () => {
    expect(isRange({ min: 1, max: 2 })).toBe(true);
    expect(isRange({ min: 1, max: 2, gapMs: { min: 1, max: 2 } })).toBe(false);
    expect(isRange([1, 2])).toBe(false);
  });
});
