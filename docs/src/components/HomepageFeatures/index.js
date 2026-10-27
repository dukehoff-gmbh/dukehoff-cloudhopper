import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Relax',
    Svg: require('@site/static/img/relax.svg').default,
    description: (
      <>
        Focus on your business logic, and we&apos;ll do the chores.
      </>
    ),
  },
  {
    title: 'Keep moving',
    Svg: require('@site/static/img/hop.svg').default,
    description: (
      <>
        Switch providers as painless as possible.
      </>
    ),
  },
  {
    title: 'Powered by Java',
    Svg: require('@site/static/img/java.svg').default,
    description: (
      <>
        Built with Java for Java devs.
      </>
    ),
  },
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" />
      </div>
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
