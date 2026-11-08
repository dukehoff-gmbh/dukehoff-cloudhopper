import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';

const FeatureList = [
  {
    title: 'Plain Java methods',
    Svg: require('@site/static/img/relax.svg').default,
    description: (
      <>
        Mark a method with <code>@Function</code> and an HTTP or cron trigger.
        You never implement a cloud SDK interface: an annotation processor
        generates the provider-specific handler classes for you.
      </>
    ),
  },
  {
    title: 'One code base, several targets',
    Svg: require('@site/static/img/hop.svg').default,
    description: (
      <>
        AWS, Azure, Google Cloud, or a Spring Boot HTTP application for on-premise
        installations. Pick the target with a Maven profile and try another
        provider without rewriting your functions.
      </>
    ),
  },
  {
    title: 'Infrastructure generated from your code',
    Svg: require('@site/static/img/java.svg').default,
    description: (
      <>
        Terraform and OpenAPI come from the same annotations, from
        configurable templates. Deploy them with your own pipeline, or with
        the Maven profile that runs Terraform for you.
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
      </div>
    </section>
  );
}
