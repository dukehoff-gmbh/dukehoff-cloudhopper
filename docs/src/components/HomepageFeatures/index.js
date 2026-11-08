import clsx from 'clsx';
import Link from '@docusaurus/Link';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Too expensive? Switch.',
    Svg: require('@site/static/img/hop.svg').default,
    description: (
      <>
        The provider is a Maven profile. Build for another cloud, deploy,
        compare the bill. Your functions stay exactly as they are.
      </>
    ),
  },
  {
    title: 'No cloud SDK in your code',
    Svg: require('@site/static/img/java.svg').default,
    description: (
      <>
        Functions are plain Java methods with <code>@Function</code> and a
        trigger. Handler classes, Terraform and API descriptions are generated
        at compile time, so nothing in your code ties you to one vendor.
      </>
    ),
  },
  {
    title: 'EU or on-premise, just another target',
    Svg: require('@site/static/img/relax.svg').default,
    description: (
      <>
        Deploy to an EU region of your cloud, or run the Spring Boot target on
        your own servers. Providers plug in through
        an <Link to="/docs/modules/deployment-config-generator-api">SPI</Link>,
        and a European cloud provider is next.
      </>
    ),
  },
];

function Feature({Svg, title, description}) {
  return (
    <div className={clsx('col col--4')}>
      <div className="text--center">
        <Svg className={styles.featureSvg} role="img" aria-hidden="true" />
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
        <p className={clsx('text--center', styles.footnote)}>
          Not every trigger exists on every target yet: the Spring Boot target
          serves HTTP functions, scheduled functions run on the three clouds.
          The <Link to="/docs/platforms/generator-overview">provider overview</Link> lists
          the differences.
        </p>
      </div>
    </section>
  );
}
